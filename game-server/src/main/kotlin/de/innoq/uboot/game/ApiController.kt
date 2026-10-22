package de.innoq.uboot.game

import de.innoq.uboot.core.SubKind
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.body

data class RankingRow(val name: String = "", val kind: String = "", val best: Int = 0, val kills: Int = 0, val rounds: Int = 0)

data class Status(
    val tick: Long,
    val clients: Int,
    val subs: Map<SubKind, Int>,
    val settings: GameSettings,
)

@RestController
@RequestMapping("/api")
class ApiController(
    private val settings: SettingsStore,
    private val loop: GameLoop,
    private val sessions: SessionRegistry,
    private val downstreams: Downstreams,
) {
    @GetMapping("/admin/settings")
    fun getSettings(): GameSettings = settings.current

    @PutMapping("/admin/settings")
    fun putSettings(@RequestBody body: GameSettings): GameSettings = settings.update(body)

    @GetMapping("/status")
    fun status(): Status {
        val world = loop.snapshot()
        return Status(
            tick = world.tick,
            clients = sessions.count(),
            subs = SubKind.entries.associateWith { kind -> world.subs.count { it.kind == kind } },
            settings = settings.current,
        )
    }

    /** Durchreichen ans Leaderboard – so sieht man im Trace auch Browser -> Game-Server -> Leaderboard -> DB */
    @GetMapping("/leaderboard")
    fun leaderboard(): ResponseEntity<List<RankingRow>> = try {
        val rows = downstreams.leaderboard.get().uri("/api/scores/top?limit=10").retrieve().body<List<RankingRow>>()
        ResponseEntity.ok(rows ?: emptyList())
    } catch (e: Exception) {
        ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(emptyList())
    }
}
