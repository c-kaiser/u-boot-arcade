package io.github.ckaiser.uboot.enemy

import io.github.ckaiser.uboot.core.BotBrain
import io.github.ckaiser.uboot.core.Controls
import io.github.ckaiser.uboot.core.DecideRequest
import io.github.ckaiser.uboot.core.DecideResponse
import io.github.ckaiser.uboot.core.LlmProtocol
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@SpringBootApplication
@ConfigurationPropertiesScan
class EnemyAiApplication

fun main(args: Array<String>) {
    runApplication<EnemyAiApplication>(*args)
}

/**
 * Entscheidet, was ein Gegner-U-Boot als Nächstes tut.
 * Mit LLM, wenn aktiviert und erreichbar – sonst regelbasierter Fallback.
 */
@RestController
@RequestMapping("/api")
class DecideController(private val llm: LlmClient) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val fallbackBrain = BotBrain()
    private val meter = GlobalOpenTelemetry.getMeter("u-boot-arcade.enemy-ai")
    private val decisions = meter.counterBuilder("enemy_ai.decisions")
        .setDescription("Entscheidungen der Gegner-KI nach Quelle (llm/fallback) und Ergebnis")
        .setUnit("{decision}")
        .build()
    private val llmCallsPerDecision = meter.histogramBuilder("enemy_ai.llm.calls_per_decision")
        .setDescription("LLM-Aufrufe pro Entscheidung (normal 1, bei Runaway-Chaos mehr)")
        .setUnit("{call}")
        .ofLongs()
        .build()

    @PostMapping("/decide")
    fun decide(@RequestBody request: DecideRequest): DecideResponse {
        val self = request.world.subs.firstOrNull { it.id == request.enemyId }
            ?: return record(DecideResponse(outcome = "unknown_enemy"))
        val span = Span.current()
        span.setAttribute("enemy.id", self.id)
        span.setAttribute("enemy.name", self.name)
        span.setAttribute("enemy.llm_enabled", request.useLlm)

        if (request.chaos.aiLatencyMs > 0) Thread.sleep(request.chaos.aiLatencyMs.toLong())

        if (!request.useLlm) return record(fallback(request, "disabled", 0))

        val rounds = if (request.chaos.aiRunaway) RUNAWAY_ROUNDS else 1
        val messages = mutableListOf(
            ChatMessage("system", LlmProtocol.SYSTEM_PROMPT),
            ChatMessage("user", LlmProtocol.userPrompt(self, request.world)),
        )
        var calls = 0
        var controls: Controls? = null
        var lastText = ""
        try {
            repeat(rounds) { round ->
                val result = llm.chat(messages)
                calls++
                lastText = result.text
                controls = LlmProtocol.parseControls(result.text) ?: controls
                if (round < rounds - 1) {
                    // Runaway: das Modell wird immer wieder zum "Nachdenken" gezwungen, der Kontext wächst
                    messages += ChatMessage("assistant", result.text)
                    messages += ChatMessage("user", "Bist du sicher? Analysiere die Lage noch einmal gründlich und antworte erneut mit JSON.")
                }
            }
        } catch (e: LlmException) {
            log.warn("LLM-Entscheidung für {} fehlgeschlagen ({}): {}", self.name, e.errorType, e.message)
            return record(fallback(request, e.errorType, calls))
        }

        val parsed = controls
        if (parsed == null) {
            log.warn("LLM-Antwort für {} nicht verwertbar: {}", self.name, lastText.take(200))
            return record(fallback(request, "parse_error", calls))
        }
        return record(DecideResponse(parsed, source = "llm", outcome = "ok", llmCalls = calls, reasoning = lastText.take(200)))
    }

    private fun fallback(request: DecideRequest, outcome: String, calls: Int): DecideResponse {
        val self = request.world.subs.first { it.id == request.enemyId }
        return DecideResponse(fallbackBrain.decide(self, request.world), source = "fallback", outcome = outcome, llmCalls = calls)
    }

    private fun record(response: DecideResponse): DecideResponse {
        decisions.add(1, Attributes.of(SOURCE, response.source, OUTCOME, response.outcome))
        if (response.llmCalls > 0) llmCallsPerDecision.record(response.llmCalls.toLong())
        Span.current().setAttribute("enemy.decision.source", response.source)
        Span.current().setAttribute("enemy.decision.outcome", response.outcome)
        return response
    }

    companion object {
        private const val RUNAWAY_ROUNDS = 8
        private val SOURCE: AttributeKey<String> = AttributeKey.stringKey("enemy.decision.source")
        private val OUTCOME: AttributeKey<String> = AttributeKey.stringKey("enemy.decision.outcome")
    }
}
