package io.github.ckaiser.uboot.swarm

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.util.Locale
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

@SpringBootApplication
@ConfigurationPropertiesScan
class PlayerSwarmApplication

fun main(args: Array<String>) {
    runApplication<PlayerSwarmApplication>(*args)
}

@ConfigurationProperties("swarm")
data class SwarmProperties(
    val url: String = "ws://localhost:8080/ws",
    val players: Int = 5,
    val rampUpMs: Long = 500,
    val inputIntervalMs: Long = 100,
    val namePrefix: String = "Schwarm",
)

@Component
class Swarm(private val props: SwarmProperties) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(javaClass)
    private val scheduler = Executors.newScheduledThreadPool(4)
    private val http = HttpClient.newHttpClient()
    private val meter = GlobalOpenTelemetry.getMeter("u-boot-arcade.player-swarm")
    private val connected = AtomicInteger()

    val rtt = meter.histogramBuilder("swarm.rtt")
        .setDescription("WebSocket-Round-Trip aus Sicht der Schwarm-Spieler")
        .setUnit("ms")
        .setExplicitBucketBoundariesAdvice(listOf(1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0, 200.0, 500.0, 1000.0))
        .build()
    val stateInterval = meter.histogramBuilder("swarm.state.interval")
        .setDescription("Abstand zwischen zwei Spielstand-Nachrichten (Soll: Tick-Intervall) – zeigt Ruckler beim Client")
        .setUnit("ms")
        .setExplicitBucketBoundariesAdvice(listOf(10.0, 25.0, 40.0, 50.0, 60.0, 75.0, 100.0, 150.0, 250.0, 500.0, 1000.0, 5000.0))
        .build()
    val connects = meter.counterBuilder("swarm.connects")
        .setDescription("Verbindungsversuche nach Ergebnis")
        .build()

    init {
        meter.gaugeBuilder("swarm.players.connected").ofLongs()
            .buildWithCallback { it.record(connected.get().toLong()) }
    }

    override fun run(args: ApplicationArguments) {
        log.info("Starte {} Schwarm-Spieler gegen {}", props.players, props.url)
        repeat(props.players) { i ->
            // Im Cluster ist das Präfix der Pod-Name – gekürzt, damit Namen kurz und unterscheidbar bleiben
            val prefix = if (props.namePrefix.length > 10) "S-" + props.namePrefix.takeLast(5) else props.namePrefix
            scheduler.schedule(Runnable { SwarmPlayer("$prefix-${i + 1}").connect() }, i * props.rampUpMs, TimeUnit.MILLISECONDS)
        }
    }

    inner class SwarmPlayer(private val name: String) : WebSocket.Listener {
        private val random = Random(name.hashCode())
        private var throttle = 0.0
        private var dive = 0.0
        private var inputTask: ScheduledFuture<*>? = null
        private var pingTask: ScheduledFuture<*>? = null
        private val buffer = StringBuilder()
        private var lastStateAt = 0L
        @Volatile private var socket: WebSocket? = null

        fun connect() {
            http.newWebSocketBuilder()
                .buildAsync(URI.create(props.url + "?client=swarm"), this)
                .whenComplete { _, error ->
                    if (error != null) {
                        connects.add(1, Attributes.of(RESULT, "error"))
                        log.debug("{} konnte nicht verbinden: {}", name, error.message)
                        reconnectLater()
                    } else {
                        connects.add(1, Attributes.of(RESULT, "ok"))
                    }
                }
        }

        override fun onOpen(webSocket: WebSocket) {
            socket = webSocket
            connected.incrementAndGet()
            webSocket.sendText("""{"type":"join","name":"$name"}""", true)
            inputTask = scheduler.scheduleAtFixedRate({ sendInput(webSocket) }, props.inputIntervalMs, props.inputIntervalMs, TimeUnit.MILLISECONDS)
            pingTask = scheduler.scheduleAtFixedRate({ sendPing(webSocket) }, 1000, 1000, TimeUnit.MILLISECONDS)
            webSocket.request(1)
        }

        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            buffer.append(data)
            if (last) {
                handleMessage(webSocket, buffer.toString())
                buffer.setLength(0)
            }
            webSocket.request(1)
            return null
        }

        override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*>? {
            disconnected()
            return null
        }

        override fun onError(webSocket: WebSocket, error: Throwable) {
            log.debug("{}: Fehler {}", name, error.message)
            disconnected()
        }

        private fun handleMessage(webSocket: WebSocket, message: String) {
            when {
                message.startsWith("""{"type":"pong"""") -> {
                    val sent = PONG_T.find(message)?.groupValues?.get(1)?.toDoubleOrNull() ?: return
                    val ms = System.nanoTime() / 1e6 - sent
                    rtt.record(ms)
                    webSocket.sendText("""{"type":"rtt","ms":${"%.2f".format(Locale.ROOT, ms)}}""", true)
                }
                message.startsWith("""{"type":"state"""") -> {
                    val now = System.nanoTime()
                    if (lastStateAt != 0L) stateInterval.record((now - lastStateAt) / 1e6)
                    lastStateAt = now
                }
            }
        }

        /** Zufällige, aber "zähe" Steuerung: ändert sich nur langsam, gelegentlich wird gefeuert */
        private fun sendInput(webSocket: WebSocket) {
            throttle = (throttle + random.nextDouble(-0.3, 0.3)).coerceIn(-1.0, 1.0)
            dive = (dive + random.nextDouble(-0.4, 0.4)).coerceIn(-1.0, 1.0) * 0.9
            val fire = random.nextDouble() < 0.1
            webSocket.sendText(
                """{"type":"input","throttle":${"%.2f".format(Locale.ROOT, throttle)},"dive":${"%.2f".format(Locale.ROOT, dive)},"fire":$fire}""",
                true,
            )
        }

        private fun sendPing(webSocket: WebSocket) {
            webSocket.sendText("""{"type":"ping","t":${"%.3f".format(Locale.ROOT, System.nanoTime() / 1e6)}}""", true)
        }

        private fun disconnected() {
            if (socket == null) return
            socket = null
            connected.decrementAndGet()
            inputTask?.cancel(false)
            pingTask?.cancel(false)
            reconnectLater()
        }

        private fun reconnectLater() {
            scheduler.schedule(Runnable { connect() }, 3, TimeUnit.SECONDS)
        }
    }

    companion object {
        private val RESULT: AttributeKey<String> = AttributeKey.stringKey("result")
        private val PONG_T = Regex(""""t":(-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)""")
    }
}
