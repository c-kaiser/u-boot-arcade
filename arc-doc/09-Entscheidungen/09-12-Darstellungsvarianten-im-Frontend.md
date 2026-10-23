# ADR-12: Darstellungsvarianten nur im Frontend

**Status:** accepted (umgesetzt)

**Datum:** 2026-10-23

## Kontext

Das Spiel hat ein Kriegs-Setting: U-Boote versenken sich gegenseitig mit Torpedos. Für den Workshop ist eine
spielerischere Variante gewünscht: KI-Agenten werfen mit KI-Slop, Menschen werfen mit Verstand. Die Gegner sind
tatsächlich LLM-gesteuert, das passt zum Thema Observability mit Langfuse.

Modell, Protokoll, Metriken (`sub.depth`, `sub.boiler.pressure`, …), Logs und Workshop-Aufgaben (Dashboards, Alerts)
sprechen U-Boot. Ein Umbenennen würde alle Services, die Telemetrie und bestehende Dashboards treffen.

## Entscheidung

- Die Slop-Arcade ist eine **Darstellungsvariante im Frontend** neben der U-Boot-Darstellung, kein Umbau des Spiels.
- `static/theme.js` enthält je Variante Texte, Anzeigenamen der Gegner und Bezeichnungen der Chaos-Schalter;
  `game.js` zeichnet je nach Variante Boote oder Figuren.
- Die Wahl gilt **pro Browser**: Knopf „Variante“ in Spiel und Admin-Panel oder `?theme=slop|uboot`, gemerkt im
  `localStorage`. Standard ist U-Boot.
- Server, `game-core`, Gegnernamen, LLM-Prompt, Metriken und Logs bleiben unverändert U-Boot. Die Slop-Variante
  übersetzt Namen und Texte nur in der Anzeige.

### Betrachtete Alternativen

- **Spiel komplett umbenennen** (Modell, Metriken, Logs, Doku): einheitlich, bricht aber Dashboards und Alerts und
  verliert die U-Boot-Variante. Verworfen.
- **Variante als serverweite Admin-Einstellung:** könnte auch Gegnernamen und LLM-Prompt umstellen, alle sähen dasselbe.
  Für den Wunsch „im Frontend umschalten“ unnötig schwer. Bleibt Option für einen Slop-Prompt.

## Konsequenzen

### Positiv
- Keine Änderung an Services, Protokoll oder Telemetrie; Workshop-Material bleibt gültig.
- Mitspielende können gleichzeitig verschiedene Varianten sehen.

### Negativ
- Zwei Darstellungen in `game.js` zu pflegen.
- Logs, Traces und Metriken sprechen auch in der Slop-Variante von U-Booten, Kesseln und Torpedos.
- Der LLM-Prompt beschreibt weiter ein U-Boot; in Langfuse ist von Slop nichts zu sehen.
- Ein Spieler, der sich wie ein Gegner nennt (z. B. „Kraken“), wird in der Slop-Variante ebenfalls umbenannt.

### Neutral
- Offen: Slop-Fassung des LLM-Prompts (serverweit) und vom LLM erzeugte Slop-Texte (kostet Tokens, daher höchstens
  optional).
