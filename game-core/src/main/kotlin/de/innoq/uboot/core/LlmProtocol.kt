package de.innoq.uboot.core

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Prompt-Bau und Antwort-Parsing für die LLM-Gegner-KI.
 * Bewusst ohne JSON-Bibliothek, damit es framework-frei testbar bleibt.
 */
object LlmProtocol {

    val SYSTEM_PROMPT = """
        Du steuerst ein feindliches U-Boot in einem 2D-Arcade-Spiel (Seitenansicht).
        x wächst nach rechts (0..${Ocean.WIDTH.toInt()} m), depth wächst nach unten (0..${Ocean.MAX_DEPTH.toInt()} m).
        Unter ${Ocean.CRUSH_DEPTH.toInt()} m wird der Rumpf zerdrückt. Über ${Ocean.OVERHEAT_PRESSURE} bar Kesseldruck überhitzt der Kessel.
        Torpedos fliegen geradeaus in Blickrichtung (facing: 1 = rechts, -1 = links) und treffen nur bei fast gleicher Tiefe (±10 m).
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
            appendLine("Dein Boot: x=${self.x.roundToInt()}, depth=${self.depth.roundToInt()}, facing=${self.facing}, hull=${self.hull.roundToInt()}, pressure=$pressure bar")
            appendLine("Gegner in der Nähe:")
            appendLine(others.ifEmpty { "- keine" })
            appendLine("Feindliche Torpedos in der Nähe: $torpedoes")
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
