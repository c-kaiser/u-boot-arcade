package de.innoq.uboot.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhysicsEngineTest {
    private val engine = PhysicsEngine(Random(42))

    private fun run(world: World, controls: Map<String, Controls>, seconds: Double, chaos: Chaos = Chaos()): Pair<World, List<GameEvent>> {
        var w = world
        val events = mutableListOf<GameEvent>()
        repeat((seconds * Ocean.TICKS_PER_SECOND).toInt()) {
            val r = engine.step(StepRequest(w, controls, 0.05, chaos))
            w = r.world
            events += r.events
        }
        return w to events
    }

    @Test
    fun `volle Fahrt beschleunigt nach rechts`() {
        val sub = Sub("a", "A", SubKind.PLAYER, x = 100.0)
        val (w, _) = run(World(subs = listOf(sub)), mapOf("a" to Controls(throttle = 1.0)), 5.0)
        val moved = w.subs.single()
        assertTrue(moved.x > 150, "x=${moved.x}")
        assertTrue(moved.vx > 10, "vx=${moved.vx}")
        assertEquals(1, moved.facing)
    }

    @Test
    fun `abtauchen erhoeht die Tiefe, auftauchen stoppt an der Oberflaeche`() {
        val sub = Sub("a", "A", SubKind.PLAYER, depth = 50.0)
        val (deep, _) = run(World(subs = listOf(sub)), mapOf("a" to Controls(dive = 1.0)), 5.0)
        assertTrue(deep.subs.single().depth > 70)
        val (up, events) = run(deep, mapOf("a" to Controls(dive = -1.0)), 30.0)
        assertEquals(0.0, up.subs.single().depth)
        assertTrue(events.any { it.type == EventType.SURFACED })
    }

    @Test
    fun `defekte Kesselkuehlung fuehrt zu Ueberhitzung und Schaden`() {
        val sub = Sub("a", "A", SubKind.PLAYER)
        val (w, events) = run(World(subs = listOf(sub)), mapOf("a" to Controls(throttle = 1.0)), 8.0, Chaos(boilerCoolingBroken = true))
        assertTrue(events.any { it.type == EventType.OVERHEAT })
        assertTrue(events.any { it.type == EventType.VALVE_VENT })
        assertTrue(w.subs.single().hull < 100)
    }

    @Test
    fun `normaler Betrieb ueberhitzt nicht`() {
        val sub = Sub("a", "A", SubKind.PLAYER)
        val (w, events) = run(World(subs = listOf(sub)), mapOf("a" to Controls(throttle = 1.0)), 20.0)
        assertFalse(events.any { it.type == EventType.OVERHEAT })
        assertEquals(100.0, w.subs.single().hull)
    }

    @Test
    fun `unter der Zerstoerungstiefe wird das Boot zerdrueckt`() {
        val sub = Sub("a", "A", SubKind.PLAYER, depth = 550.0)
        val (_, events) = run(World(subs = listOf(sub)), mapOf("a" to Controls(dive = 1.0)), 15.0)
        assertTrue(events.any { it.type == EventType.CRUSH_DEPTH })
        val destroyed = events.first { it.type == EventType.DESTROYED }
        assertEquals("crush", destroyed.detail)
    }

    @Test
    fun `drei Torpedotreffer zerstoeren das Ziel und geben Punkte`() {
        val shooter = Sub("s", "Shooter", SubKind.PLAYER, x = 500.0, depth = 100.0, facing = 1)
        val target = Sub("t", "Target", SubKind.ENEMY, x = 650.0, depth = 100.0)
        val (w, events) = run(
            World(subs = listOf(shooter, target)),
            mapOf("s" to Controls(fire = true)),
            6.0,
        )
        assertTrue(events.count { it.type == EventType.HIT } >= 3, events.toString())
        assertTrue(events.any { it.type == EventType.KILL && it.subId == "s" && it.otherId == "t" })
        assertTrue(events.any { it.type == EventType.DESTROYED && it.subId == "t" && it.detail == "torpedo" })
        val s = w.subs.first { it.id == "s" }
        assertEquals(1, s.kills)
        assertTrue(s.score >= 100)
    }

    @Test
    fun `zerstoertes Boot taucht nach Wartezeit wieder auf`() {
        val dead = Sub("a", "A", SubKind.BOT, alive = false, respawnIn = 1.0, deaths = 2, hull = 0.0)
        val (w, events) = run(World(subs = listOf(dead)), emptyMap(), 1.5)
        val sub = w.subs.single()
        assertTrue(sub.alive)
        assertEquals(100.0, sub.hull)
        assertEquals(2, sub.deaths)
        assertTrue(events.any { it.type == EventType.RESPAWN })
    }

    @Test
    fun `Leck laesst Boote sinken`() {
        val sub = Sub("a", "A", SubKind.PLAYER, depth = 100.0)
        val (w, _) = run(World(subs = listOf(sub)), emptyMap(), 10.0, Chaos(hullLeak = true))
        val s = w.subs.single()
        assertTrue(s.depth > 100.0)
        assertTrue(s.hull < 95.0)
    }

    @Test
    fun `Boote bleiben in der Welt`() {
        val sub = Sub("a", "A", SubKind.PLAYER, x = 10.0)
        val (w, _) = run(World(subs = listOf(sub)), mapOf("a" to Controls(throttle = -1.0)), 10.0)
        assertTrue(w.subs.single().x >= 0.0)
    }
}
