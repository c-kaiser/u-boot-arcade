package io.github.ckaiser.uboot.game

import io.github.ckaiser.uboot.core.Chaos
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicReference

/** Zur Laufzeit änderbare Spieleinstellungen (Admin-Panel). */
data class GameSettings(
    val bots: Int = 3,
    val enemies: Int = 2,
    val llmEnabled: Boolean = false,
    val chaos: Chaos = Chaos(),
) {
    fun sanitized() = copy(
        bots = bots.coerceIn(0, MAX_BOTS),
        enemies = enemies.coerceIn(0, ENEMY_NAMES.size),
        chaos = chaos.copy(
            // unter dem Physik-Timeout (2 s) bleiben, sonst friert die Welt nur noch ein
            physicsLatencyMs = chaos.physicsLatencyMs.coerceIn(0, 1500),
            physicsErrorRate = chaos.physicsErrorRate.coerceIn(0.0, 1.0),
            current = chaos.current.coerceIn(-10.0, 10.0),
            leaderboardSlowMs = chaos.leaderboardSlowMs.coerceIn(0, 10_000),
            aiLatencyMs = chaos.aiLatencyMs.coerceIn(0, 10_000),
        ),
    )

    companion object {
        const val MAX_BOTS = 60
        val ENEMY_NAMES = listOf("SlopGPT", "Halluzinator", "Buzzwordinator", "Copypasta", "Promptimus")
    }
}

@Component
class SettingsStore(props: GameProperties) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val ref = AtomicReference(
        GameSettings(bots = props.bots, enemies = props.enemies, llmEnabled = props.llmEnabled).sanitized(),
    )

    val current: GameSettings get() = ref.get()

    fun update(settings: GameSettings): GameSettings {
        val sanitized = settings.sanitized()
        val previous = ref.getAndSet(sanitized)
        if (previous != sanitized) log.info("Einstellungen geändert: {} -> {}", previous, sanitized)
        return sanitized
    }
}
