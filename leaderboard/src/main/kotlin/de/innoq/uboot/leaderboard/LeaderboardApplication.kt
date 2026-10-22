package de.innoq.uboot.leaderboard

import de.innoq.uboot.core.ScoreEntry
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@SpringBootApplication
class LeaderboardApplication

fun main(args: Array<String>) {
    runApplication<LeaderboardApplication>(*args)
}

data class RankingRow(
    val name: String,
    val kind: String,
    val best: Int,
    val kills: Int,
    val rounds: Int,
)

@RestController
@RequestMapping("/api/scores")
class ScoreController(private val jdbc: JdbcClient) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val meter = GlobalOpenTelemetry.getMeter("u-boot-arcade.leaderboard")
    private val submitted = meter.counterBuilder("leaderboard.scores.submitted")
        .setDescription("Eingetragene Runden")
        .build()
    private val scoreHistogram = meter.histogramBuilder("leaderboard.score")
        .setDescription("Punkte pro Runde")
        .setUnit("{point}")
        .ofLongs()
        .build()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun submit(
        @RequestBody entry: ScoreEntry,
        @RequestHeader(name = "X-Chaos-Slow-Ms", required = false) slowMs: Int?,
    ) {
        // Chaos: langsame Datenbank – sieht man schön als DB-Span im Trace
        if (slowMs != null && slowMs > 0) {
            jdbc.sql("SELECT pg_sleep(:seconds)").param("seconds", slowMs / 1000.0).query().singleRow()
        }
        jdbc.sql("INSERT INTO scores (name, kind, score, kills, cause) VALUES (:name, :kind, :score, :kills, :cause)")
            .param("name", entry.name.take(64))
            .param("kind", entry.kind.name)
            .param("score", entry.score)
            .param("kills", entry.kills)
            .param("cause", entry.cause.take(32))
            .update()

        val attrs = Attributes.of(SUB_KIND, entry.kind.name, CAUSE, entry.cause)
        submitted.add(1, attrs)
        scoreHistogram.record(entry.score.toLong(), attrs)
        log.info("Runde eingetragen: {} ({}) {} Punkte, {} Abschüsse, Ende durch {}", entry.name, entry.kind, entry.score, entry.kills, entry.cause)
    }

    @GetMapping("/top")
    fun top(@RequestParam(defaultValue = "10") limit: Int): List<RankingRow> =
        jdbc.sql(
            """
            SELECT name, kind, MAX(score) AS best, SUM(kills) AS kills, COUNT(*) AS rounds
            FROM scores
            GROUP BY name, kind
            ORDER BY best DESC, kills DESC
            LIMIT :limit
            """.trimIndent(),
        )
            .param("limit", limit.coerceIn(1, 100))
            .query { rs, _ ->
                RankingRow(rs.getString("name"), rs.getString("kind"), rs.getInt("best"), rs.getInt("kills"), rs.getInt("rounds"))
            }
            .list()

    companion object {
        private val SUB_KIND: AttributeKey<String> = AttributeKey.stringKey("sub.kind")
        private val CAUSE: AttributeKey<String> = AttributeKey.stringKey("game.destroy.cause")
    }
}
