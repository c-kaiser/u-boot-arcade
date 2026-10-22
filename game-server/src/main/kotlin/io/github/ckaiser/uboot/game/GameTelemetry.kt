package io.github.ckaiser.uboot.game

import io.github.ckaiser.uboot.core.Sub
import io.github.ckaiser.uboot.core.SubKind
import io.github.ckaiser.uboot.core.World
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.ObservableDoubleMeasurement
import org.springframework.stereotype.Component
import kotlin.math.abs

/**
 * Alle eigenen Metriken des Game-Servers an einer Stelle.
 * Fachliche Werte (Tiefe, Druck, ...) werden pro Boot als Observable Gauges gemeldet –
 * der Wert wird beim Export (Standard: alle 60 s, per OTEL_METRIC_EXPORT_INTERVAL änderbar) abgelesen.
 */
@Component
class GameTelemetry(private val sessions: SessionRegistry) {
    val tracer = GlobalOpenTelemetry.getTracer("u-boot-arcade.game-server")
    private val meter = GlobalOpenTelemetry.getMeter("u-boot-arcade.game-server")

    @Volatile
    var latestWorld: World = World()

    val tickDuration = meter.histogramBuilder("game.tick.duration")
        .setDescription("Dauer eines Spiel-Ticks inkl. Physik-Aufruf und Broadcast")
        .setUnit("ms")
        .setExplicitBucketBoundariesAdvice(listOf(1.0, 2.0, 5.0, 10.0, 20.0, 35.0, 50.0, 75.0, 100.0, 200.0, 500.0, 1000.0, 2500.0))
        .build()
    val tickOverruns = meter.counterBuilder("game.tick.overruns")
        .setDescription("Ticks, die länger als das Tick-Intervall gedauert haben (Spiel ruckelt)")
        .build()
    val physicsFailures = meter.counterBuilder("game.physics.failures")
        .setDescription("Fehlgeschlagene Physik-Aufrufe (Welt friert für diesen Tick ein)")
        .build()
    val events = meter.counterBuilder("game.events")
        .setDescription("Spielereignisse (Treffer, Abschüsse, Überhitzung, ...)")
        .setUnit("{event}")
        .build()
    val clientRtt = meter.histogramBuilder("game.client.rtt")
        .setDescription("Round-Trip-Zeit zwischen Client und Game-Server über WebSocket")
        .setUnit("ms")
        .setExplicitBucketBoundariesAdvice(listOf(1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0, 200.0, 500.0, 1000.0))
        .build()
    val wsMessages = meter.counterBuilder("game.websocket.messages")
        .setDescription("WebSocket-Nachrichten")
        .build()
    val stateSize = meter.histogramBuilder("game.state.size")
        .setDescription("Größe des pro Tick an alle Clients gesendeten Spielstands")
        .setUnit("By")
        .ofLongs()
        .build()
    val enemyDecisions = meter.counterBuilder("game.enemy.decisions")
        .setDescription("Antworten der Gegner-KI aus Sicht des Game-Servers")
        .build()
    val enemyDecisionLatency = meter.histogramBuilder("game.enemy.decision.latency")
        .setDescription("Zeit vom Anfragen bis zum Eintreffen einer Gegner-Entscheidung")
        .setUnit("ms")
        .setExplicitBucketBoundariesAdvice(listOf(5.0, 10.0, 25.0, 50.0, 100.0, 250.0, 500.0, 1000.0, 2500.0, 5000.0, 10000.0, 30000.0))
        .build()
    val framesDropped = meter.counterBuilder("game.websocket.frames_dropped")
        .setDescription("Spielstände, die ein Client nicht rechtzeitig abgenommen hat (langsame Verbindung)")
        .build()
    val leaderboardDropped = meter.counterBuilder("game.leaderboard.dropped")
        .setDescription("Runden, die nicht ans Leaderboard gingen (Warteschlange voll oder Fehler)")
        .build()

    init {
        sessions.onFrameDropped = { framesDropped.add(1, Attributes.of(CLIENT_KIND, it.kind)) }

        meter.gaugeBuilder("game.players.connected")
            .setDescription("Verbundene WebSocket-Clients")
            .ofLongs()
            .buildWithCallback { it.record(sessions.count().toLong()) }

        meter.gaugeBuilder("game.subs.alive")
            .setDescription("Lebende U-Boote nach Typ")
            .ofLongs()
            .buildWithCallback { m ->
                val world = latestWorld
                SubKind.entries.forEach { kind ->
                    m.record(world.subs.count { it.alive && it.kind == kind }.toLong(), Attributes.of(SUB_KIND, kind.name))
                }
            }

        subGauge("sub.depth", "Tauchtiefe", "m") { it.depth }
        subGauge("sub.speed", "Geschwindigkeit über Grund", "m/s") { abs(it.vx) }
        subGauge("sub.boiler.pressure", "Dampfdruck im Kessel", "bar") { it.boilerPressure }
        subGauge("sub.boiler.temperature", "Kesseltemperatur", "Cel") { it.boilerTemp }
        subGauge("sub.hull", "Rumpfintegrität", "%") { it.hull }
        subGauge("sub.score", "Punkte der laufenden Runde", "{point}") { it.score.toDouble() }
    }

    private fun subGauge(name: String, description: String, unit: String, value: (Sub) -> Double) {
        meter.gaugeBuilder(name)
            .setDescription(description)
            .setUnit(unit)
            .buildWithCallback { m: ObservableDoubleMeasurement ->
                latestWorld.subs.filter { it.alive }.forEach { m.record(value(it), subAttributes(it)) }
            }
    }

    companion object {
        val SUB_KIND: AttributeKey<String> = AttributeKey.stringKey("sub.kind")
        val SUB_NAME: AttributeKey<String> = AttributeKey.stringKey("sub.name")
        val EVENT_TYPE: AttributeKey<String> = AttributeKey.stringKey("game.event.type")
        val CLIENT_KIND: AttributeKey<String> = AttributeKey.stringKey("client.kind")
        val DIRECTION: AttributeKey<String> = AttributeKey.stringKey("direction")
        val MESSAGE_TYPE: AttributeKey<String> = AttributeKey.stringKey("message.type")
        val ERROR_TYPE: AttributeKey<String> = AttributeKey.stringKey("error.type")
        val SOURCE: AttributeKey<String> = AttributeKey.stringKey("enemy.decision.source")
        val OUTCOME: AttributeKey<String> = AttributeKey.stringKey("enemy.decision.outcome")
        val REASON: AttributeKey<String> = AttributeKey.stringKey("reason")

        fun subAttributes(sub: Sub): Attributes = Attributes.of(SUB_NAME, sub.name, SUB_KIND, sub.kind.name)
    }
}
