package io.github.ckaiser.uboot.game

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.ckaiser.uboot.core.BotBrain
import io.github.ckaiser.uboot.core.Controls
import io.github.ckaiser.uboot.core.DecideRequest
import io.github.ckaiser.uboot.core.DecideResponse
import io.github.ckaiser.uboot.core.EventType
import io.github.ckaiser.uboot.core.GameEvent
import io.github.ckaiser.uboot.core.PhysicsEngine
import io.github.ckaiser.uboot.core.ScoreEntry
import io.github.ckaiser.uboot.core.StepRequest
import io.github.ckaiser.uboot.core.StepResponse
import io.github.ckaiser.uboot.core.Sub
import io.github.ckaiser.uboot.core.SubKind
import io.github.ckaiser.uboot.core.World
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.body
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.math.round

/** Kompakte Sicht auf ein Boot für die Clients (spart Bandbreite bei vielen Booten). */
data class SubView(
    val id: String, val name: String, val kind: SubKind,
    val x: Double, val depth: Double, val vx: Double, val facing: Int,
    val pressure: Double, val temp: Double, val hull: Double,
    val alive: Boolean, val respawnIn: Double,
    val score: Int, val kills: Int, val deaths: Int,
    val overheating: Boolean, val belowCrushDepth: Boolean,
)

data class TorpedoView(val x: Double, val depth: Double, val vx: Double, val ownerId: String)

data class StateMessage(
    val type: String = "state",
    val tick: Long,
    val serverTime: Long,
    val tickMillis: Long,
    val subs: List<SubView>,
    val torpedoes: List<TorpedoView>,
    val events: List<GameEvent>,
    val names: Map<String, String>,
    val physicsOk: Boolean,
    val llmEnabled: Boolean,
    val chaosActive: List<String>,
)

/**
 * Herzstück: tickt 20x pro Sekunde. Pro Tick ein Trace "game.tick" mit
 * Physik-Aufruf (HTTP, automatisch instrumentiert) und Broadcast an alle Clients.
 */
@Component
class GameLoop(
    private val props: GameProperties,
    private val settings: SettingsStore,
    private val downstreams: Downstreams,
    private val sessions: SessionRegistry,
    private val telemetry: GameTelemetry,
    private val mapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val scheduler = Executors.newSingleThreadScheduledExecutor { Thread(it, "game-tick") }
    private val enemyExecutor = Executors.newFixedThreadPool(4) { Thread(it, "enemy-ai").apply { isDaemon = true } }
    private val leaderboardExecutor = ThreadPoolExecutor(
        2, 2, 0, TimeUnit.MILLISECONDS, LinkedBlockingQueue(200),
        { Thread(it, "leaderboard").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy(),
    )
    private val botBrain = BotBrain()

    @Volatile private var world = World()
    private val joins = ConcurrentLinkedQueue<Sub>()
    private val leaves = ConcurrentLinkedQueue<String>()
    private val controls = ConcurrentHashMap<String, Controls>()
    private val enemyControls = ConcurrentHashMap<String, Controls>()
    private val enemyInFlight = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var physicsOk = true

    /** Chaos "Memory Leak": hier sammelt sich Müll an, bis der Schalter wieder aus ist */
    private val leakedSnapshots = mutableListOf<ByteArray>()

    @PostConstruct
    fun start() {
        scheduler.scheduleAtFixedRate(::safeTick, 1000, props.tickMillis, TimeUnit.MILLISECONDS)
        log.info("Spiel läuft mit {} ms pro Tick", props.tickMillis)
    }

    @PreDestroy
    fun stop() {
        scheduler.shutdownNow()
        enemyExecutor.shutdownNow()
        leaderboardExecutor.shutdownNow()
    }

    fun join(name: String): String {
        val id = "p-" + UUID.randomUUID().toString().take(8)
        joins += PhysicsEngine.spawn(id, name.take(16), SubKind.PLAYER)
        return id
    }

    fun leave(subId: String) {
        leaves += subId
        controls.remove(subId)
    }

    fun setControls(subId: String, c: Controls) {
        controls[subId] = c
    }

    fun snapshot(): World = world

    private fun safeTick() {
        try {
            tick()
        } catch (e: Throwable) {
            // Niemals die Schleife sterben lassen
            log.error("Tick fehlgeschlagen", e)
        }
    }

    private fun tick() {
        val started = System.nanoTime()
        val current = settings.current
        val span = telemetry.tracer.spanBuilder("game.tick").setNoParent().startSpan()
        try {
            span.makeCurrent().use {
                val before = reconcileRoster(world, current)
                span.setAttribute("game.tick", before.tick + 1)
                span.setAttribute("game.subs", before.subs.size.toLong())
                span.setAttribute("game.players", before.subs.count { it.kind == SubKind.PLAYER }.toLong())

                if (before.tick % props.enemyDecisionEveryTicks.coerceAtLeast(1) == 0L) requestEnemyDecisions(before, current)

                val tickControls = HashMap<String, Controls>(controls)
                before.subs.forEach { sub ->
                    when (sub.kind) {
                        SubKind.BOT -> tickControls[sub.id] = botBrain.decide(sub, before)
                        SubKind.ENEMY -> tickControls[sub.id] = enemyControls[sub.id] ?: Controls()
                        SubKind.PLAYER -> {}
                    }
                }

                val step = callPhysics(StepRequest(before, tickControls, props.tickMillis / 1000.0, current.chaos))
                physicsOk = step != null
                if (step == null) {
                    span.setStatus(StatusCode.ERROR, "physics unavailable")
                }
                val after = step?.world ?: before
                val events = step?.events ?: emptyList()
                world = after
                telemetry.latestWorld = after

                handleEvents(before, after, events, current)
                broadcast(after, events, current)

                if (current.chaos.gameServerMemoryLeak) {
                    synchronized(leakedSnapshots) { leakedSnapshots += ByteArray(64 * 1024) }
                    span.setAttribute("chaos.leaked_snapshots", leakedSnapshots.size.toLong())
                } else if (leakedSnapshots.isNotEmpty()) {
                    synchronized(leakedSnapshots) { leakedSnapshots.clear() }
                    log.info("Memory-Leak-Chaos beendet, Speicher wieder freigegeben")
                }
            }
        } finally {
            span.end()
            val ms = (System.nanoTime() - started) / 1e6
            telemetry.tickDuration.record(ms)
            if (ms > props.tickMillis) telemetry.tickOverruns.add(1)
        }
    }

    /** Spieler hinzufügen/entfernen, Anzahl Bots und Gegner an die Einstellungen anpassen */
    private fun reconcileRoster(w: World, s: GameSettings): World {
        val subs = w.subs.toMutableList()
        val left = HashSet<String>()
        while (true) left += leaves.poll() ?: break
        while (true) {
            val joined = joins.poll() ?: break
            // Beitritt und Verlassen im selben Tick: gar nicht erst aufnehmen
            if (joined.id !in left) subs += joined
        }
        if (left.isNotEmpty()) subs.removeAll { it.id in left }

        val bots = subs.filter { it.kind == SubKind.BOT }
        if (bots.size < s.bots) {
            val existing = bots.map { it.id }.toSet()
            (1..GameSettings.MAX_BOTS).asSequence()
                .map { "bot-$it" }
                .filter { it !in existing }
                .take(s.bots - bots.size)
                .forEach { id -> subs += PhysicsEngine.spawn(id, "Bot-" + id.removePrefix("bot-"), SubKind.BOT) }
        } else if (bots.size > s.bots) {
            val remove = bots.sortedByDescending { it.id.removePrefix("bot-").toIntOrNull() ?: 0 }.take(bots.size - s.bots).map { it.id }.toSet()
            subs.removeAll { it.id in remove }
        }

        val enemies = subs.filter { it.kind == SubKind.ENEMY }
        val wanted = GameSettings.ENEMY_NAMES.take(s.enemies)
        val present = enemies.map { it.name }.toSet()
        wanted.filter { it !in present }.forEach { name ->
            subs += PhysicsEngine.spawn("enemy-" + name.lowercase(), name, SubKind.ENEMY)
        }
        subs.removeAll { it.kind == SubKind.ENEMY && it.name !in wanted }
        enemyControls.keys.retainAll(subs.filter { it.kind == SubKind.ENEMY }.map { it.id }.toSet())

        return w.copy(subs = subs)
    }

    private fun callPhysics(request: StepRequest): StepResponse? = try {
        downstreams.physics.post().uri("/api/step").body(request).retrieve().body<StepResponse>()
    } catch (e: Exception) {
        telemetry.physicsFailures.add(1, Attributes.of(GameTelemetry.ERROR_TYPE, e.javaClass.simpleName))
        if (physicsOk) log.warn("Physik-Service nicht verfügbar: {} – Welt friert ein", e.message)
        null
    }

    /** Asynchron: die Antwort wird erst in einem späteren Tick wirksam. Langsame KI = Gegner reagieren träge. */
    private fun requestEnemyDecisions(w: World, s: GameSettings) {
        w.subs.filter { it.kind == SubKind.ENEMY && it.alive }.forEach { enemy ->
            if (!enemyInFlight.add(enemy.id)) return@forEach
            val requested = System.nanoTime()
            val task = Context.current().wrap(Runnable {
                try {
                    val response = downstreams.enemyAi.post().uri("/api/decide")
                        .body(DecideRequest(enemy.id, w, s.llmEnabled, s.chaos))
                        .retrieve()
                        .body<DecideResponse>()
                    if (response != null) {
                        enemyControls[enemy.id] = response.controls
                        telemetry.enemyDecisions.add(1, Attributes.of(GameTelemetry.SOURCE, response.source, GameTelemetry.OUTCOME, response.outcome))
                    }
                } catch (e: Exception) {
                    telemetry.enemyDecisions.add(1, Attributes.of(GameTelemetry.SOURCE, "none", GameTelemetry.OUTCOME, "unreachable"))
                    log.debug("Gegner-KI nicht erreichbar: {}", e.message)
                } finally {
                    telemetry.enemyDecisionLatency.record((System.nanoTime() - requested) / 1e6)
                    enemyInFlight.remove(enemy.id)
                }
            })
            try {
                enemyExecutor.execute(task)
            } catch (e: RejectedExecutionException) {
                enemyInFlight.remove(enemy.id)
            }
        }
    }

    private fun handleEvents(before: World, after: World, events: List<GameEvent>, s: GameSettings) {
        if (events.isEmpty()) return
        val byId = after.subs.associateBy { it.id }
        val beforeById = before.subs.associateBy { it.id }
        for (event in events) {
            val sub = byId[event.subId] ?: beforeById[event.subId] ?: continue
            telemetry.events.add(1, Attributes.of(GameTelemetry.EVENT_TYPE, event.type.name, GameTelemetry.SUB_KIND, sub.kind.name))
            when (event.type) {
                EventType.DESTROYED -> {
                    val killer = event.otherId?.let { byId[it]?.name }
                    log.info(
                        "{} ({}) zerstört durch {}{} – Runde endet mit {} Punkten",
                        sub.name, sub.kind, event.detail, killer?.let { " von $it" } ?: "", sub.score,
                    )
                    submitScore(ScoreEntry(sub.name, sub.kind, sub.score, sub.kills, event.detail), s)
                }
                EventType.OVERHEAT -> log.warn("Kessel von {} überhitzt: {}", sub.name, event.detail)
                EventType.CRUSH_DEPTH -> log.warn("{} unter Zerstörungstiefe: {}", sub.name, event.detail)
                EventType.VALVE_VENT -> log.warn("Sicherheitsventil bei {} hat abgeblasen", sub.name)
                else -> {}
            }
        }
    }

    private fun submitScore(entry: ScoreEntry, s: GameSettings) {
        val task = Context.current().wrap(Runnable {
            try {
                downstreams.leaderboard.post().uri("/api/scores")
                    .header("X-Chaos-Slow-Ms", s.chaos.leaderboardSlowMs.toString())
                    .body(entry)
                    .retrieve()
                    .toBodilessEntity()
            } catch (e: Exception) {
                telemetry.leaderboardDropped.add(1, Attributes.of(GameTelemetry.REASON, "error"))
                log.warn("Leaderboard-Eintrag für {} fehlgeschlagen: {}", entry.name, e.message)
            }
        })
        try {
            leaderboardExecutor.execute(task)
        } catch (e: RejectedExecutionException) {
            telemetry.leaderboardDropped.add(1, Attributes.of(GameTelemetry.REASON, "queue_full"))
        }
    }

    private fun broadcast(w: World, events: List<GameEvent>, s: GameSettings) {
        if (sessions.count() == 0) return
        val span = telemetry.tracer.spanBuilder("game.broadcast").startSpan()
        try {
            val message = StateMessage(
                tick = w.tick,
                serverTime = System.currentTimeMillis(),
                tickMillis = props.tickMillis,
                subs = w.subs.map { it.toView() },
                torpedoes = w.torpedoes.map { TorpedoView(r1(it.x), r1(it.depth), r1(it.vx), it.ownerId) },
                events = events.filter { it.type != EventType.TORPEDO_FIRED },
                names = w.subs.associate { it.id to it.name },
                physicsOk = physicsOk,
                llmEnabled = s.llmEnabled,
                chaosActive = activeChaos(s),
            )
            val payload = mapper.writeValueAsString(message)
            val sent = sessions.broadcast(payload)
            telemetry.stateSize.record(payload.length.toLong())
            telemetry.wsMessages.add(sent.toLong(), Attributes.of(GameTelemetry.DIRECTION, "out", GameTelemetry.MESSAGE_TYPE, "state"))
            span.setAttribute("game.clients", sent.toLong())
            span.setAttribute("game.state.bytes", payload.length.toLong())
        } finally {
            span.end()
        }
    }

    private fun activeChaos(s: GameSettings): List<String> = buildList {
        val c = s.chaos
        if (c.physicsLatencyMs > 0) add("Physik +${c.physicsLatencyMs} ms")
        if (c.physicsErrorRate > 0) add("Physik-Fehler ${(c.physicsErrorRate * 100).toInt()} %")
        if (c.boilerCoolingBroken) add("Kesselkühlung defekt")
        if (c.hullLeak) add("Leck im Rumpf")
        if (c.current != 0.0) add("Strömung ${c.current}")
        if (c.leaderboardSlowMs > 0) add("Leaderboard +${c.leaderboardSlowMs} ms")
        if (c.aiLatencyMs > 0) add("KI +${c.aiLatencyMs} ms")
        if (c.aiRunaway) add("KI-Endlosschleife")
        if (c.gameServerMemoryLeak) add("Memory Leak")
    }

    private fun Sub.toView() = SubView(
        id, name, kind, r1(x), r1(depth), r1(vx), facing, r1(boilerPressure), r1(boilerTemp), r1(hull),
        alive, r1(respawnIn), score, kills, deaths, overheating, belowCrushDepth,
    )

    private fun r1(v: Double) = round(v * 10) / 10
}
