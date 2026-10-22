package de.innoq.uboot.physics

import de.innoq.uboot.core.PhysicsEngine
import de.innoq.uboot.core.StepRequest
import de.innoq.uboot.core.StepResponse
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import kotlin.random.Random

@SpringBootApplication
class PhysicsApplication

fun main(args: Array<String>) {
    runApplication<PhysicsApplication>(*args)
}

/**
 * Zustandslos: bekommt die komplette Welt und liefert die nächste zurück.
 * Lässt sich deshalb beliebig horizontal skalieren (im Cluster: mehrere Replicas).
 */
@RestController
@RequestMapping("/api")
class StepController {
    private val log = LoggerFactory.getLogger(javaClass)
    private val engine = PhysicsEngine()
    private val tracer = GlobalOpenTelemetry.getTracer("u-boot-arcade.physics")
    private val meter = GlobalOpenTelemetry.getMeter("u-boot-arcade.physics")

    private val eventCounter = meter.counterBuilder("physics.events")
        .setDescription("Spielereignisse, die die Physik erzeugt hat")
        .setUnit("{event}")
        .build()
    private val entityHistogram = meter.histogramBuilder("physics.step.entities")
        .setDescription("Anzahl simulierter Objekte (Boote + Torpedos) pro Step")
        .setUnit("{entity}")
        .ofLongs()
        .build()
    private val chaosCounter = meter.counterBuilder("chaos.injected")
        .setDescription("Ausgelöste Chaos-Störungen")
        .build()

    @PostMapping("/step")
    fun step(@RequestBody request: StepRequest): StepResponse {
        val chaos = request.chaos
        val current = Span.current()
        current.setAttribute("game.tick", request.world.tick)

        if (chaos.physicsLatencyMs > 0) {
            val span = tracer.spanBuilder("chaos latency").startSpan()
            try {
                span.setAttribute("chaos.latency_ms", chaos.physicsLatencyMs.toLong())
                Thread.sleep(chaos.physicsLatencyMs.toLong())
            } finally {
                span.end()
            }
            chaosCounter.add(1, Attributes.of(CHAOS_KIND, "physics_latency"))
        }

        if (chaos.physicsErrorRate > 0 && Random.nextDouble() < chaos.physicsErrorRate) {
            chaosCounter.add(1, Attributes.of(CHAOS_KIND, "physics_error"))
            current.setStatus(StatusCode.ERROR, "chaos: physics error")
            log.warn("Chaos: Physik-Step für Tick {} absichtlich fehlgeschlagen", request.world.tick)
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Physik-Kern überlastet (Chaos)")
        }

        val span = tracer.spanBuilder("physics.simulate").startSpan()
        return try {
            span.makeCurrent().use {
                val response = engine.step(request)
                val entities = (response.world.subs.size + response.world.torpedoes.size).toLong()
                span.setAttribute("game.subs", response.world.subs.size.toLong())
                span.setAttribute("game.torpedoes", response.world.torpedoes.size.toLong())
                span.setAttribute("game.events", response.events.size.toLong())
                entityHistogram.record(entities)
                response.events.forEach { eventCounter.add(1, Attributes.of(EVENT_TYPE, it.type.name)) }
                response
            }
        } finally {
            span.end()
        }
    }

    companion object {
        private val EVENT_TYPE: AttributeKey<String> = AttributeKey.stringKey("game.event.type")
        private val CHAOS_KIND: AttributeKey<String> = AttributeKey.stringKey("chaos.kind")
    }
}
