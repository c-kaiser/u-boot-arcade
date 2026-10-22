package io.github.ckaiser.uboot.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BotAndLlmTest {

    @Test
    fun `Bot faehrt auf das naechste Ziel zu`() {
        val bot = Sub("b", "Bot", SubKind.BOT, x = 200.0, depth = 100.0)
        val target = Sub("p", "Player", SubKind.PLAYER, x = 900.0, depth = 200.0)
        val c = BotBrain(Random(1)).decide(bot, World(subs = listOf(bot, target)))
        assertTrue(c.throttle > 0)
        assertTrue(c.dive > 0)
    }

    @Test
    fun `Bot meidet die Zerstoerungstiefe`() {
        val bot = Sub("b", "Bot", SubKind.BOT, depth = Ocean.CRUSH_DEPTH - 10)
        val target = Sub("p", "Player", SubKind.PLAYER, depth = Ocean.MAX_DEPTH)
        val c = BotBrain().decide(bot, World(subs = listOf(bot, target)))
        assertEquals(-1.0, c.dive)
    }

    @Test
    fun `Bots gewinnen gegen ein stillstehendes Ziel`() {
        val engine = PhysicsEngine(Random(7))
        val brain = BotBrain(Random(7))
        var world = World(
            subs = listOf(
                Sub("b", "Bot", SubKind.BOT, x = 300.0, depth = 100.0),
                Sub("p", "Dummy", SubKind.PLAYER, x = 900.0, depth = 160.0),
            ),
        )
        val events = mutableListOf<GameEvent>()
        repeat(60 * Ocean.TICKS_PER_SECOND) {
            val bot = world.subs.first { it.id == "b" }
            val r = engine.step(StepRequest(world, mapOf("b" to brain.decide(bot, world))))
            world = r.world
            events += r.events
        }
        assertTrue(events.any { it.type == EventType.KILL && it.subId == "b" }, "Bot hat in 60 s nichts getroffen")
    }

    @Test
    fun `LLM-Antwort mit JSON wird geparst`() {
        val c = LlmProtocol.parseControls("""Klar! {"throttle": -0.5, "dive": 0.25, "fire": true} Viel Glück""")
        assertNotNull(c)
        assertEquals(-0.5, c.throttle)
        assertEquals(0.25, c.dive)
        assertTrue(c.fire)
    }

    @Test
    fun `LLM-Antwort wird begrenzt und fehlende Felder bekommen Defaults`() {
        val c = LlmProtocol.parseControls("""```json
            {"throttle": 3}
            ```""")
        assertNotNull(c)
        assertEquals(1.0, c.throttle)
        assertEquals(0.0, c.dive)
        assertEquals(false, c.fire)
    }

    @Test
    fun `Unsinn vom LLM ergibt null`() {
        assertNull(LlmProtocol.parseControls("Ich würde gerne angreifen."))
        assertNull(LlmProtocol.parseControls("""{"dive": 1}"""))
    }

    @Test
    fun `Prompt enthaelt eigene Lage und Gegner`() {
        val self = Sub("e", "SlopGPT", SubKind.ENEMY, x = 100.0, depth = 50.0)
        val other = Sub("p", "Christian", SubKind.PLAYER, x = 400.0, depth = 80.0)
        val prompt = LlmProtocol.userPrompt(self, World(subs = listOf(self, other)))
        assertTrue(prompt.startsWith("Du: x=100, depth=50"))
        assertTrue(prompt.contains("- Christian (player): x=400, depth=80"))
    }
}
