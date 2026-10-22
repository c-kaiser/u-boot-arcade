package io.github.ckaiser.uboot.core

import kotlin.math.abs
import kotlin.random.Random

/** Die Spielwelt: ein seitlicher Schnitt durchs Meer. */
object Ocean {
    const val WIDTH = 1600.0
    const val MAX_DEPTH = 600.0
    const val CRUSH_DEPTH = 420.0
    const val OVERHEAT_PRESSURE = 9.5
    const val VALVE_PRESSURE = 11.5
    const val NOMINAL_PRESSURE = 5.0
    const val TORPEDO_DAMAGE = 40.0
    const val HIT_RADIUS_X = 20.0
    const val HIT_RADIUS_DEPTH = 10.0
    const val RESPAWN_SECONDS = 3.0
    const val TICKS_PER_SECOND = 20
}

/**
 * Zustandslose Physik: aus Welt + Steuerbefehlen wird die nächste Welt.
 * Läuft im Physik-Service, ist aber frei von Frameworks und damit direkt testbar.
 */
class PhysicsEngine(private val random: Random = Random.Default) {

    fun step(request: StepRequest): StepResponse {
        val dt = request.dt.coerceIn(0.001, 0.5)
        val chaos = request.chaos
        val tick = request.world.tick + 1
        val events = mutableListOf<GameEvent>()
        val newTorpedoes = mutableListOf<Torpedo>()

        val movedSubs = request.world.subs.map { sub ->
            if (!sub.alive) {
                respawnIfReady(sub, dt, events)
            } else {
                moveSub(sub, request.controls[sub.id] ?: Controls(), dt, chaos, tick, events, newTorpedoes)
            }
        }.associateBy { it.id }.toMutableMap()

        val survivingTorpedoes = mutableListOf<Torpedo>()
        for (t in request.world.torpedoes) {
            val moved = t.copy(x = t.x + t.vx * dt, ttl = t.ttl - dt)
            if (moved.ttl <= 0 || moved.x < 0 || moved.x > Ocean.WIDTH) continue

            val target = movedSubs.values.firstOrNull {
                it.alive && it.id != moved.ownerId &&
                    abs(it.x - moved.x) < Ocean.HIT_RADIUS_X &&
                    abs(it.depth - moved.depth) < Ocean.HIT_RADIUS_DEPTH
            }
            if (target == null) {
                survivingTorpedoes += moved
                continue
            }
            events += GameEvent(EventType.HIT, target.id, moved.ownerId)
            val damaged = target.copy(hull = target.hull - Ocean.TORPEDO_DAMAGE, lastDamageCause = "torpedo")
            if (damaged.hull <= 0) {
                movedSubs[target.id] = destroy(damaged, "torpedo", events, by = moved.ownerId)
                movedSubs[moved.ownerId]?.let { owner ->
                    movedSubs[owner.id] = owner.copy(score = owner.score + 100, kills = owner.kills + 1)
                    events += GameEvent(EventType.KILL, owner.id, target.id)
                }
            } else {
                movedSubs[target.id] = damaged
            }
        }

        val world = World(
            tick = tick,
            subs = request.world.subs.map { movedSubs.getValue(it.id) },
            torpedoes = survivingTorpedoes + newTorpedoes,
        )
        return StepResponse(world, events)
    }

    private fun moveSub(
        sub: Sub, controls: Controls, dt: Double, chaos: Chaos, tick: Long,
        events: MutableList<GameEvent>, torpedoes: MutableList<Torpedo>,
    ): Sub {
        val throttle = controls.throttle.coerceIn(-1.0, 1.0)
        val dive = controls.dive.coerceIn(-1.0, 1.0)
        val facing = when {
            throttle > 0.05 -> 1
            throttle < -0.05 -> -1
            else -> sub.facing
        }
        var hull = sub.hull
        var cause = sub.lastDamageCause

        // Kessel: Druck läuft träge auf einen Zielwert zu, der von der Fahrstufe abhängt
        val targetPressure = 4.0 + 5.0 * abs(throttle) + if (chaos.boilerCoolingBroken) 6.0 else 0.0
        var pressure = sub.boilerPressure + (targetPressure - sub.boilerPressure) * 0.6 * dt
        if (pressure > Ocean.VALVE_PRESSURE) {
            pressure = 7.0
            events += GameEvent(EventType.VALVE_VENT, sub.id, detail = "Sicherheitsventil abgeblasen")
        }
        val overheating = pressure > Ocean.OVERHEAT_PRESSURE
        if (overheating) {
            hull -= 6.0 * dt
            cause = "overheat"
            if (!sub.overheating) events += GameEvent(EventType.OVERHEAT, sub.id, detail = "%.1f bar".format(pressure))
        }
        val boilerTemp = 100.0 + pressure * 16.0

        // Antrieb: weniger Dampf = weniger Schub
        val pressureFactor = (pressure / Ocean.NOMINAL_PRESSURE).coerceIn(0.2, 1.3)
        val ax = throttle * 10.0 * pressureFactor - 0.45 * sub.vx + chaos.current
        var vx = sub.vx + ax * dt
        var x = sub.x + vx * dt
        if (x < 0) { x = 0.0; vx = -vx * 0.3 }
        if (x > Ocean.WIDTH) { x = Ocean.WIDTH; vx = -vx * 0.3 }

        // Tauchzellen
        val ay = dive * 12.0 - 1.5 * sub.vy + if (chaos.hullLeak) 1.6 else 0.0
        var vy = sub.vy + ay * dt
        var depth = sub.depth + vy * dt
        if (depth <= 0) {
            if (sub.depth > 0.0) events += GameEvent(EventType.SURFACED, sub.id)
            depth = 0.0; vy = 0.0
        }
        if (depth > Ocean.MAX_DEPTH) { depth = Ocean.MAX_DEPTH; vy = 0.0 }

        val belowCrush = depth > Ocean.CRUSH_DEPTH
        if (belowCrush) {
            hull -= (depth - Ocean.CRUSH_DEPTH) * 0.08 * dt
            cause = "crush"
            if (!sub.belowCrushDepth) events += GameEvent(EventType.CRUSH_DEPTH, sub.id, detail = "%.0f m".format(depth))
        }
        if (chaos.hullLeak) {
            hull -= 1.0 * dt
            if (!overheating && !belowCrush) cause = "leak"
        }

        var cooldown = (sub.torpedoCooldown - dt).coerceAtLeast(0.0)
        if (controls.fire && cooldown <= 0.0) {
            cooldown = 1.2
            torpedoes += Torpedo(
                id = "${sub.id}-t$tick",
                ownerId = sub.id,
                x = x + facing * 22.0,
                depth = depth,
                vx = facing * 70.0 + vx * 0.5,
                ttl = 2.5,
            )
            events += GameEvent(EventType.TORPEDO_FIRED, sub.id)
        }

        val survivalBonus = if (tick % Ocean.TICKS_PER_SECOND == 0L) 1 else 0
        val moved = sub.copy(
            x = x, depth = depth, vx = vx, vy = vy, facing = facing, throttle = throttle,
            boilerPressure = pressure, boilerTemp = boilerTemp, hull = hull,
            torpedoCooldown = cooldown, score = sub.score + survivalBonus,
            overheating = overheating, belowCrushDepth = belowCrush, lastDamageCause = cause,
        )
        return if (hull <= 0) destroy(moved, cause.ifEmpty { "unknown" }, events) else moved
    }

    private fun destroy(sub: Sub, cause: String, events: MutableList<GameEvent>, by: String? = null): Sub {
        events += GameEvent(EventType.DESTROYED, sub.id, by, detail = cause)
        return sub.copy(
            hull = 0.0, alive = false, deaths = sub.deaths + 1, respawnIn = Ocean.RESPAWN_SECONDS,
            vx = 0.0, vy = 0.0, lastDamageCause = cause, overheating = false, belowCrushDepth = false,
        )
    }

    private fun respawnIfReady(sub: Sub, dt: Double, events: MutableList<GameEvent>): Sub {
        val remaining = sub.respawnIn - dt
        if (remaining > 0) return sub.copy(respawnIn = remaining)
        events += GameEvent(EventType.RESPAWN, sub.id)
        return spawn(sub.id, sub.name, sub.kind, random).copy(deaths = sub.deaths)
    }

    companion object {
        /** Neues Boot an zufälliger Position, frische Runde (Score/Kills auf 0) */
        fun spawn(id: String, name: String, kind: SubKind, random: Random = Random.Default): Sub = Sub(
            id = id,
            name = name,
            kind = kind,
            x = random.nextDouble(100.0, Ocean.WIDTH - 100.0),
            depth = random.nextDouble(30.0, 150.0),
            facing = if (random.nextBoolean()) 1 else -1,
        )
    }
}
