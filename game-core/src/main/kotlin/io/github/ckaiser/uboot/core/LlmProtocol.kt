package io.github.ckaiser.uboot.core

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Prompt-Bau und Antwort-Parsing für die LLM-Gegner-KI.
 * Bewusst ohne JSON-Bibliothek, damit es framework-frei testbar bleibt.
 */
object LlmProtocol {

    val SYSTEM_PROMPT = """
        Du bist ein übereifriger KI-Agent in einem 2D-Arcade-Spiel (Seitenansicht) und wirfst mit KI-Slop nach den Menschen.
        x wächst nach rechts (0..${Ocean.WIDTH.toInt()}), depth wächst nach unten (0..${Ocean.MAX_DEPTH.toInt()}); je tiefer, desto mehr Slop.
        Unter depth ${Ocean.CRUSH_DEPTH.toInt()} versinkst du selbst im Slop und verlierst Geduld (hull).
        Über ${Ocean.OVERHEAT_PRESSURE} Denkdruck (pressure) raucht dir der Kopf.
        Slop fliegt geradeaus in Blickrichtung (facing: 1 = rechts, -1 = links) und trifft nur bei fast gleicher Höhe (depth ±10).
        Antworte AUSSCHLIESSLICH mit einem JSON-Objekt, ohne Erklärung:
        {"throttle": <-1..1, negativ = nach links>, "dive": <-1..1, positiv = tiefer>, "fire": <true|false>}
    """.trimIndent()

    fun userPrompt(self: Sub, world: World): String {
        val others = world.subs
            .filter { it.alive && it.id != self.id }
            .sortedBy { abs(it.x - self.x) }
            .take(4)
            .joinToString("\n") {
                "- ${it.name} (${it.kind.name.lowercase()}): x=${it.x.roundToInt()}, depth=${it.depth.roundToInt()}, hull=${it.hull.roundToInt()}"
            }
        val torpedoes = world.torpedoes.count { it.ownerId != self.id && abs(it.x - self.x) < 300 }
        val pressure = "%.1f".format(java.util.Locale.ROOT, self.boilerPressure)
        return buildString {
            appendLine("Du: x=${self.x.roundToInt()}, depth=${self.depth.roundToInt()}, facing=${self.facing}, hull=${self.hull.roundToInt()}, pressure=$pressure")
            appendLine("Menschen und andere Figuren in der Nähe:")
            appendLine(others.ifEmpty { "- keine" })
            appendLine("Geistesblitze, die auf dich zufliegen: $torpedoes")
            append("Was tust du jetzt?")
        }
    }

    private val objectRegex = Regex("""\{[^{}]*\}""", RegexOption.DOT_MATCHES_ALL)
    private fun numberField(name: String) = Regex(""""$name"\s*:\s*(-?\d+(?:\.\d+)?)""")
    private val fireField = Regex(""""fire"\s*:\s*(true|false)""", RegexOption.IGNORE_CASE)

    /** Liefert null, wenn die Antwort kein verwertbares JSON enthält. */
    fun parseControls(text: String): Controls? {
        val obj = objectRegex.findAll(text).firstOrNull { numberField("throttle").containsMatchIn(it.value) }?.value
            ?: return null
        val throttle = numberField("throttle").find(obj)?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
        val dive = numberField("dive").find(obj)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        val fire = fireField.find(obj)?.groupValues?.get(1)?.lowercase() == "true"
        return Controls(throttle.coerceIn(-1.0, 1.0), dive.coerceIn(-1.0, 1.0), fire)
    }
}
