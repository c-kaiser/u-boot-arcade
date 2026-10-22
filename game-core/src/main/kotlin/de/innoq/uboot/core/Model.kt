package de.innoq.uboot.core

/** Wer steuert ein U-Boot? */
enum class SubKind { PLAYER, BOT, ENEMY }

/**
 * Ein U-Boot. Einheiten: Meter, Sekunden, Bar, Grad Celsius.
 * Unveränderlich – die Physik liefert pro Tick neue Instanzen.
 */
data class Sub(
    val id: String,
    val name: String,
    val kind: SubKind,
    val x: Double = 100.0,
    val depth: Double = 50.0,
    val vx: Double = 0.0,
    val vy: Double = 0.0,
    val facing: Int = 1,
    val throttle: Double = 0.0,
    val boilerPressure: Double = 5.0,
    val boilerTemp: Double = 180.0,
    val hull: Double = 100.0,
    val torpedoCooldown: Double = 0.0,
    val score: Int = 0,
    val kills: Int = 0,
    val deaths: Int = 0,
    val alive: Boolean = true,
    val respawnIn: Double = 0.0,
    val overheating: Boolean = false,
    val belowCrushDepth: Boolean = false,
    val lastDamageCause: String = "",
)

data class Torpedo(
    val id: String,
    val ownerId: String,
    val x: Double,
    val depth: Double,
    val vx: Double,
    val ttl: Double,
)

/** Steuerbefehl für einen Tick. throttle: -1 (volle Kraft zurück) .. 1, dive: -1 (auftauchen) .. 1 (abtauchen). */
data class Controls(
    val throttle: Double = 0.0,
    val dive: Double = 0.0,
    val fire: Boolean = false,
)

/** Schalter zum Kaputtmachen. Wird vom Game-Server an alle Services durchgereicht. */
data class Chaos(
    /** Künstliche Verzögerung im Physik-Service pro Step (ms) */
    val physicsLatencyMs: Int = 0,
    /** Anteil der Physik-Requests, die mit 503 scheitern (0..1) */
    val physicsErrorRate: Double = 0.0,
    /** Kesselkühlung defekt: Dampfdruck steigt über die Sicherheitsgrenze */
    val boilerCoolingBroken: Boolean = false,
    /** Leck im Rumpf: alle Boote verlieren Rumpfintegrität und sinken */
    val hullLeak: Boolean = false,
    /** Meeresströmung (m/s², positiv = nach rechts) */
    val current: Double = 0.0,
    /** Künstliche DB-Verzögerung im Leaderboard (ms, via pg_sleep) */
    val leaderboardSlowMs: Int = 0,
    /** Künstliche Verzögerung in der Gegner-KI (ms) */
    val aiLatencyMs: Int = 0,
    /** Gegner-KI "denkt nach" und fragt das LLM immer wieder (Kosten!) */
    val aiRunaway: Boolean = false,
    /** Game-Server hält bei jedem Tick eine Kopie des Spielstands fest */
    val gameServerMemoryLeak: Boolean = false,
)

data class World(
    val tick: Long = 0,
    val subs: List<Sub> = emptyList(),
    val torpedoes: List<Torpedo> = emptyList(),
)

data class GameEvent(
    val type: EventType,
    val subId: String,
    val otherId: String? = null,
    val detail: String = "",
)

enum class EventType {
    TORPEDO_FIRED, HIT, KILL, DESTROYED, RESPAWN,
    OVERHEAT, VALVE_VENT, CRUSH_DEPTH, SURFACED,
}

data class StepRequest(
    val world: World = World(),
    val controls: Map<String, Controls> = emptyMap(),
    val dt: Double = 0.05,
    val chaos: Chaos = Chaos(),
)

data class StepResponse(
    val world: World = World(),
    val events: List<GameEvent> = emptyList(),
)

/** Anfrage an die Gegner-KI */
data class DecideRequest(
    val enemyId: String = "",
    val world: World = World(),
    val useLlm: Boolean = false,
    val chaos: Chaos = Chaos(),
)

data class DecideResponse(
    val controls: Controls = Controls(),
    /** "llm" oder "fallback" */
    val source: String = "fallback",
    /** ok, disabled, timeout, http_error, parse_error */
    val outcome: String = "disabled",
    val llmCalls: Int = 0,
    val reasoning: String = "",
)

/** Ergebnis einer Runde (ein Leben) für das Leaderboard */
data class ScoreEntry(
    val name: String = "",
    val kind: SubKind = SubKind.PLAYER,
    val score: Int = 0,
    val kills: Int = 0,
    val cause: String = "",
)
