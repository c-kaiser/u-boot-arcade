package io.github.ckaiser.uboot.game

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.ckaiser.uboot.core.Controls
import io.opentelemetry.api.common.Attributes
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Verbundene Clients. Getrennt vom Handler, damit GameLoop und Handler sich nicht gegenseitig brauchen. */
@Component
class SessionRegistry {
    private val log = LoggerFactory.getLogger(javaClass)

    class Client(val session: WebSocketSession, val kind: String) {
        @Volatile var subId: String? = null
        /** Nur der jeweils neueste Spielstand wird gesendet – langsame Clients bekommen einfach weniger Frames */
        val pending = AtomicReference<String?>(null)
        val sending = AtomicBoolean(false)
    }

    private val clients = ConcurrentHashMap<String, Client>()
    // Virtuelle Threads: ein hängender Client blockiert weder den Tick noch andere Clients
    private val sender = Executors.newVirtualThreadPerTaskExecutor()

    /** Wird vom Telemetrie-Bean gesetzt, um verworfene Frames zu zählen */
    @Volatile var onFrameDropped: (Client) -> Unit = {}

    fun add(raw: WebSocketSession, kind: String): Client {
        // Mehrere Threads senden (Tick-Loop, Pong) – der Decorator serialisiert das
        val session = ConcurrentWebSocketSessionDecorator(raw, 2_000, 1024 * 1024)
        return Client(session, kind).also { clients[raw.id] = it }
    }

    fun remove(sessionId: String): Client? = clients.remove(sessionId)
    fun get(sessionId: String): Client? = clients[sessionId]
    fun count(): Int = clients.size

    /** Stellt den Spielstand für alle beigetretenen Clients bereit, ohne auf langsame Verbindungen zu warten. */
    fun broadcast(payload: String): Int {
        var queued = 0
        clients.values.forEach { client ->
            if (client.subId == null || !client.session.isOpen) return@forEach
            if (client.pending.getAndSet(payload) != null) onFrameDropped(client)
            queued++
            if (client.sending.compareAndSet(false, true)) sender.execute { drain(client) }
        }
        return queued
    }

    private fun drain(client: Client) {
        while (true) {
            while (true) {
                val next = client.pending.getAndSet(null) ?: break
                try {
                    if (client.session.isOpen) client.session.sendMessage(TextMessage(next))
                } catch (e: Exception) {
                    log.debug("Senden an {} fehlgeschlagen: {}", client.session.id, e.message)
                }
            }
            client.sending.set(false)
            // Falls zwischen "leer" und "fertig" ein neuer Frame kam, selbst weitermachen
            if (client.pending.get() == null || !client.sending.compareAndSet(false, true)) return
        }
    }
}

/**
 * WebSocket-Protokoll (JSON):
 *   Client -> Server: join {name, client}, input {throttle, dive, fire}, ping {t}, rtt {ms}
 *   Server -> Client: welcome {id}, state {...}, pong {t}
 */
@Component
class GameWebSocketHandler(
    private val sessions: SessionRegistry,
    private val loop: GameLoop,
    private val mapper: ObjectMapper,
    private val telemetry: GameTelemetry,
) : TextWebSocketHandler() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val kind = if (session.uri?.query?.contains("client=swarm") == true) "swarm" else "browser"
        sessions.add(session, kind)
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val client = sessions.get(session.id) ?: return
        val node = try {
            mapper.readTree(message.payload)
        } catch (e: Exception) {
            return
        }
        val type = node.path("type").asText("")
        telemetry.wsMessages.add(1, Attributes.of(GameTelemetry.DIRECTION, "in", GameTelemetry.MESSAGE_TYPE, type))

        when (type) {
            "join" -> if (client.subId == null) {
                val name = node.path("name").asText("").trim().ifEmpty { "Mensch" }
                val subId = loop.join(name)
                client.subId = subId
                client.session.sendMessage(TextMessage(mapper.writeValueAsString(mapOf("type" to "welcome", "id" to subId))))
                log.info("{}-Client {} tritt bei als {} ({})", client.kind, session.id, name, subId)
            }
            "input" -> client.subId?.let {
                loop.setControls(
                    it,
                    Controls(
                        throttle = node.path("throttle").asDouble(0.0),
                        dive = node.path("dive").asDouble(0.0),
                        fire = node.path("fire").asBoolean(false),
                    ),
                )
            }
            "ping" -> client.session.sendMessage(
                TextMessage("""{"type":"pong","t":${node.path("t").asDouble(0.0)}}"""),
            )
            "rtt" -> {
                val ms = node.path("ms").asDouble(-1.0)
                if (ms in 0.0..60_000.0) telemetry.clientRtt.record(ms, Attributes.of(GameTelemetry.CLIENT_KIND, client.kind))
            }
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        sessions.remove(session.id)?.subId?.let {
            loop.leave(it)
            log.info("Client {} getrennt ({}), Boot {} entfernt", session.id, status.code, it)
        }
    }
}
