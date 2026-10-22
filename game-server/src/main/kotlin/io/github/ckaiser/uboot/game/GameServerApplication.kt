package io.github.ckaiser.uboot.game

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import java.net.http.HttpClient
import java.time.Duration

@SpringBootApplication
@ConfigurationPropertiesScan
class GameServerApplication

fun main(args: Array<String>) {
    runApplication<GameServerApplication>(*args)
}

@ConfigurationProperties("game")
data class GameProperties(
    val physicsUrl: String = "http://localhost:8081",
    val enemyAiUrl: String = "http://localhost:8083",
    val leaderboardUrl: String = "http://localhost:8082",
    val tickMillis: Long = 50,
    val bots: Int = 3,
    val enemies: Int = 2,
    val llmEnabled: Boolean = false,
    val enemyDecisionEveryTicks: Int = 20,
)

/** Die drei Downstream-Services. Die HTTP-Spans dazu erzeugt der OTel-Java-Agent automatisch. */
class Downstreams(val physics: RestClient, val enemyAi: RestClient, val leaderboard: RestClient)

@Configuration
class ClientConfig {
    @Bean
    fun downstreams(
        props: GameProperties,
        physicsBuilder: RestClient.Builder,
        enemyBuilder: RestClient.Builder,
        leaderboardBuilder: RestClient.Builder,
    ) = Downstreams(
        physics = physicsBuilder.baseUrl(props.physicsUrl).requestFactory(factory(Duration.ofSeconds(2))).build(),
        // Großzügig: bei "KI-Endlosschleife" fragt die Gegner-KI das LLM bis zu 8x hintereinander
        enemyAi = enemyBuilder.baseUrl(props.enemyAiUrl).requestFactory(factory(Duration.ofSeconds(90))).build(),
        leaderboard = leaderboardBuilder.baseUrl(props.leaderboardUrl).requestFactory(factory(Duration.ofSeconds(10))).build(),
    )

    private fun factory(readTimeout: Duration): JdkClientHttpRequestFactory {
        val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
        return JdkClientHttpRequestFactory(http).apply { setReadTimeout(readTimeout) }
    }
}

@Configuration
@EnableWebSocket
class WebSocketConfig(private val handler: GameWebSocketHandler) : WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(handler, "/ws").setAllowedOriginPatterns("*")
    }
}
