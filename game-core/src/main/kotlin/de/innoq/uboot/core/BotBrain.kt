package de.innoq.uboot.core

import kotlin.math.abs
import kotlin.math.sign
import kotlin.random.Random

/**
 * Einfache, regelbasierte KI. Wird für Server-Bots genutzt und als Fallback,
 * wenn die LLM-Gegner-KI nicht verfügbar ist oder Unsinn antwortet.
 */
class BotBrain(private val random: Random = Random.Default) {

    fun decide(self: Sub, world: World): Controls {
        if (!self.alive) return Controls()

        val target = world.subs
            .filter { it.alive && it.id != self.id && (self.kind != SubKind.ENEMY || it.kind != SubKind.ENEMY) }
            .minByOrNull { abs(it.x - self.x) + abs(it.depth - self.depth) * 2 }
            ?: return patrol(self)

        val dx = target.x - self.x
        val dDepth = target.depth - self.depth

        // Abstand halten (~150 m), damit Torpedos Zeit zum Laufen haben
        var throttle = when {
            abs(dx) > 250 -> sign(dx) * 0.9
            abs(dx) < 80 -> -sign(dx) * 0.6
            else -> sign(dx) * 0.25
        }
        // Kessel schonen
        if (self.boilerPressure > Ocean.OVERHEAT_PRESSURE - 1.0) throttle *= 0.3

        var dive = (dDepth / 40.0).coerceIn(-1.0, 1.0)
        // Nicht unter die Zerstörungstiefe
        if (self.depth > Ocean.CRUSH_DEPTH - 40) dive = -1.0

        val facingTarget = sign(dx).toInt() == self.facing
        val aligned = abs(dDepth) < 12
        val inRange = abs(dx) in 40.0..400.0
        val fire = facingTarget && aligned && inRange && random.nextDouble() < 0.6

        return Controls(throttle = throttle, dive = dive, fire = fire)
    }

    private fun patrol(self: Sub): Controls {
        val throttle = if (self.x < 200) 0.6 else if (self.x > Ocean.WIDTH - 200) -0.6 else self.facing * 0.4
        val dive = ((120.0 - self.depth) / 50.0).coerceIn(-1.0, 1.0)
        return Controls(throttle = throttle, dive = dive)
    }
}
