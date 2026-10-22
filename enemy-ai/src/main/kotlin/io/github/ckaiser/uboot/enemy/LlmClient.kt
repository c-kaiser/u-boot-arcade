package io.github.ckaiser.uboot.enemy

import com.fasterxml.jackson.databind.ObjectMapper
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.time.Duration

@ConfigurationProperties("llm")
data class LlmProperties(
    val baseUrl: String = "http://litellm:4000/v1",
    val model: String = "enemy-brain",
    val apiKey: String = "sk-local",
    val timeoutMs: Long = 4000,
    val maxTokens: Int = 60,
    val priceInputPerMtok: Double = 0.0,
    val priceOutputPerMtok: Double = 0.0,
)

data class ChatMessage(val role: String, val content: String)

data class ChatResult(
    val text: String,
    val inputTokens: Long,
    val outputTokens: Long,
    val model: String,
)

class LlmException(val errorType: String, message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Minimaler OpenAI-kompatibler Chat-Client mit OpenTelemetry-GenAI-Instrumentierung:
 * Span "chat <model>" mit gen_ai.*-Attributen sowie die Standard-Metriken
 * gen_ai.client.token.usage und gen_ai.client.operation.duration.
 * Den HTTP-Span darunter erzeugt der Java-Agent automatisch (inkl. traceparent an LiteLLM).
 */
@Component
class LlmClient(private val props: LlmProperties, private val mapper: ObjectMapper) {
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
    private val tracer = GlobalOpenTelemetry.getTracer("u-boot-arcade.enemy-ai")
    private val meter = GlobalOpenTelemetry.getMeter("u-boot-arcade.enemy-ai")

    private val tokenUsage = meter.histogramBuilder("gen_ai.client.token.usage")
        .setDescription("Anzahl verbrauchter Tokens pro LLM-Aufruf")
        .setUnit("{token}")
        .ofLongs()
        .setExplicitBucketBoundariesAdvice(listOf(1L, 4L, 16L, 64L, 256L, 1024L, 4096L, 16384L, 65536L))
        .build()
    private val operationDuration = meter.histogramBuilder("gen_ai.client.operation.duration")
        .setDescription("Dauer eines LLM-Aufrufs")
        .setUnit("s")
        .setExplicitBucketBoundariesAdvice(listOf(0.01, 0.02, 0.04, 0.08, 0.16, 0.32, 0.64, 1.28, 2.56, 5.12, 10.24))
        .build()
    private val cost = meter.counterBuilder("enemy_ai.llm.cost")
        .setDescription("Geschätzte LLM-Kosten aus Token-Verbrauch und konfigurierten Preisen")
        .setUnit("USD")
        .ofDoubles()
        .build()

    private val endpoint = URI.create(props.baseUrl.trimEnd('/') + "/chat/completions")

    fun chat(messages: List<ChatMessage>): ChatResult {
        val span = tracer.spanBuilder("chat ${props.model}")
            .setSpanKind(SpanKind.CLIENT)
            .setAttribute(OPERATION, "chat")
            .setAttribute(PROVIDER, "openai")
            .setAttribute(REQUEST_MODEL, props.model)
            .setAttribute("gen_ai.request.max_tokens", props.maxTokens.toLong())
            .setAttribute("gen_ai.request.temperature", 0.3)
            .setAttribute("server.address", endpoint.host)
            .setAttribute("server.port", (if (endpoint.port > 0) endpoint.port else 443).toLong())
            .startSpan()
        val started = System.nanoTime()
        val baseAttrs = Attributes.of(OPERATION, "chat", PROVIDER, "openai", REQUEST_MODEL, props.model)

        try {
            span.makeCurrent().use {
                val body = mapper.writeValueAsString(
                    mapOf(
                        "model" to props.model,
                        "messages" to messages,
                        "max_tokens" to props.maxTokens,
                        "temperature" to 0.3,
                    ),
                )
                val request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofMillis(props.timeoutMs))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer ${props.apiKey}")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build()

                val response = try {
                    http.send(request, HttpResponse.BodyHandlers.ofString())
                } catch (e: HttpTimeoutException) {
                    throw LlmException("timeout", "LLM hat nicht innerhalb von ${props.timeoutMs} ms geantwortet", e)
                } catch (e: java.io.IOException) {
                    throw LlmException("connection_error", "LLM nicht erreichbar: ${e.message}", e)
                }
                if (response.statusCode() >= 400) {
                    throw LlmException("http_${response.statusCode()}", "LLM antwortete mit HTTP ${response.statusCode()}")
                }

                val json = mapper.readTree(response.body())
                val choice = json.path("choices").path(0)
                val text = choice.path("message").path("content").asText("")
                val inputTokens = json.path("usage").path("prompt_tokens").asLong(0)
                val outputTokens = json.path("usage").path("completion_tokens").asLong(0)
                val responseModel = json.path("model").asText(props.model)

                span.setAttribute("gen_ai.response.model", responseModel)
                span.setAttribute("gen_ai.usage.input_tokens", inputTokens)
                span.setAttribute("gen_ai.usage.output_tokens", outputTokens)
                choice.path("finish_reason").asText(null)?.let {
                    span.setAttribute(AttributeKey.stringArrayKey("gen_ai.response.finish_reasons"), listOf(it))
                }

                tokenUsage.record(inputTokens, baseAttrs.toBuilder().put(TOKEN_TYPE, "input").build())
                tokenUsage.record(outputTokens, baseAttrs.toBuilder().put(TOKEN_TYPE, "output").build())
                val usd = inputTokens * props.priceInputPerMtok / 1_000_000 + outputTokens * props.priceOutputPerMtok / 1_000_000
                cost.add(usd, baseAttrs)
                span.setAttribute("enemy_ai.llm.cost_usd", usd)
                operationDuration.record(seconds(started), baseAttrs)

                return ChatResult(text, inputTokens, outputTokens, responseModel)
            }
        } catch (e: LlmException) {
            span.setAttribute("error.type", e.errorType)
            span.setStatus(StatusCode.ERROR, e.message ?: e.errorType)
            span.recordException(e)
            operationDuration.record(seconds(started), baseAttrs.toBuilder().put(ERROR_TYPE, e.errorType).build())
            throw e
        } catch (e: Exception) {
            span.setStatus(StatusCode.ERROR, e.message ?: "error")
            span.recordException(e)
            operationDuration.record(seconds(started), baseAttrs.toBuilder().put(ERROR_TYPE, e.javaClass.simpleName).build())
            throw LlmException("invalid_response", "Unerwartete LLM-Antwort: ${e.message}", e)
        } finally {
            span.end()
        }
    }

    private fun seconds(startedNanos: Long) = (System.nanoTime() - startedNanos) / 1e9

    companion object {
        private val OPERATION: AttributeKey<String> = AttributeKey.stringKey("gen_ai.operation.name")
        private val PROVIDER: AttributeKey<String> = AttributeKey.stringKey("gen_ai.provider.name")
        private val REQUEST_MODEL: AttributeKey<String> = AttributeKey.stringKey("gen_ai.request.model")
        private val TOKEN_TYPE: AttributeKey<String> = AttributeKey.stringKey("gen_ai.token.type")
        private val ERROR_TYPE: AttributeKey<String> = AttributeKey.stringKey("error.type")
    }
}
