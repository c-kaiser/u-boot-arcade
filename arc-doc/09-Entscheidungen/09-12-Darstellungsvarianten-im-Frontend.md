# ADR-12: Darstellungsvarianten nur im Frontend

**Status:** accepted (umgesetzt)

**Datum:** 2026-10-23

## Kontext

Das Spiel hat ein Kriegs-Setting: U-Boote versenken sich gegenseitig mit Torpedos. Für den Workshop ist eine
spielerischere Variante gewünscht (F13): KI-Agenten werfen mit KI-Slop, Menschen werfen mit Verstand. Die Gegner
werden optional per LLM gesteuert (sonst regelbasiert), das passt zum Thema Observability mit Langfuse.

Modell, Protokoll, Metriken (`sub.depth`, `sub.boiler.pressure`, …), Logs und Workshop-Aufgaben (Dashboards, Alerts)
sprechen U-Boot. Ein Umbenennen würde alle Services, die Telemetrie und bestehende Dashboards treffen.

## Entscheidung

- Die Slop-Arcade ist eine **Darstellungsvariante im Frontend** neben der U-Boot-Darstellung, kein Umbau des Spiels.
- `static/theme.js` enthält je Variante Texte, Anzeigenamen der Gegner und Bezeichnungen der Chaos-Schalter;
  `game.js` zeichnet je nach Variante Boote oder Figuren.
- Die Wahl gilt **pro Browser**: Knopf „Variante“ in Spiel und Admin-Panel (gemerkt im `localStorage`, offene Tabs
  übernehmen den Wechsel) oder `?theme=slop|uboot` (nur für diesen Aufruf, hat Vorrang vor der gemerkten Wahl).
  Standard ist U-Boot.
- Server, `game-core`, Gegnernamen, LLM-Prompt, Metriken und Logs bleiben unverändert U-Boot. Die Slop-Variante
  übersetzt Namen und Texte nur in der Anzeige.

### Anzeige in der Slop-Variante

| U-Boot (Modell, Telemetrie) | Slop-Anzeige |
| --- | --- |
| Spieler, Bot `Bot-N` | Mensch, Bot als „Kolleg:in N“ |
| Gegner Kraken, Leviathan, Nautilus, Moby, Nessie | KI-Agenten SlopGPT, Halluzinator, Buzzwordinator, Copypasta, Promptimus |
| Tiefe in m, Zerstörungstiefe | Slop-Pegel in % der maximalen Tiefe, „Ab hier versinkt man im Slop“ |
| Fahrt in kn | Tempo in km/h |
| Dampfdruck in bar, Kessel überhitzt | Denkdruck als „kühl / heiß / raucht!“, „Kopf raucht“; Kesseltemperatur ausgeblendet |
| Rumpf | Geduld |
| Torpedo | Geistesblitz (Mensch) bzw. Slop-Wurf (Agent) |
| Chaos Kesselkühlung defekt, Leck im Rumpf, Meeresströmung, KI-Endlosschleife | Kaffeemaschine kaputt, Benachrichtigungsflut, Hype-Welle, Agent denkt nach (Endlosschleife) |

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
- Anzeigewerte der Slop-Variante sind umgerechnet (Prozent, km/h) oder nur qualitativ (Denkdruck). Ein direkter
  Abgleich mit `sub.depth` oder `sub.boiler.pressure` (Qualitätsziel 1) geht nur in der U-Boot-Variante; für
  Übungen mit Metriken daher die U-Boot-Variante nutzen.
- Die Zuordnung in `theme.js` hängt an Server-Texten (`GameSettings.ENEMY_NAMES`, Chaos-Bezeichnungen im `GameLoop`).
  Ändert sich dort ein Text, zeigt die Slop-Variante stillschweigend den U-Boot-Text (TD12).
- Der LLM-Prompt beschreibt weiter ein U-Boot; in Langfuse ist von Slop nichts zu sehen.
- Namen wie „Kraken“ oder „Bot-3“ werden in der Slop-Variante überall umbenannt (Spielfeld, Ereignisse, Bestenliste),
  auch wenn ein Mensch sich so nennt.

### Neutral
- Offen: Slop-Fassung des LLM-Prompts (serverweit) und vom LLM erzeugte Slop-Texte (kostet Tokens, daher höchstens
  optional).
