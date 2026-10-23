# Ideen

Sammlung von Ideen rund um das Spiel – noch nicht entschieden und nicht verbindlich.
Was davon umgesetzt wird, landet als GitHub Issue bzw. in der [arc42-Doku](arc-doc/).

## Störungen (Chaos)

**Zielbild:** In der UI lässt sich für jedes U-Boot eine Störung auf einer Skala einstellen. Je nach Stärke löst sie
gezieltes Chaos aus, etwa zusätzliche Latenz. Wie die Störungen technisch umgesetzt werden, ist noch offen.

**Heute** gibt es globale Schalter im Admin-Panel (`admin.html`), die für alle Boote gelten:

| Schalter | Wirkung | Woran man es erkennt |
| --- | --- | --- |
| Physik-Latenz | Jeder Step wird verzögert | `game.tick.duration` steigt, Overruns, Spiel ruckelt |
| Physik-Fehlerrate | Steps scheitern mit 503 | Fehler-Spans, `game.physics.failures`, Welt friert ein |
| Leaderboard langsam | Künstliche Verzögerung vor jedem Insert (`Thread.sleep`) | Langer Request-Span am Leaderboard, Warteschlange läuft voll |
| Gegner-KI-Latenz | Gegner reagieren träge | `game.enemy.decision.latency` |
| Gegner-KI-Endlosschleife | 8 LLM-Aufrufe pro Entscheidung, Kontext wächst | Tokens und Kosten explodieren, lange Traces |
| Memory Leak | Heap wächst um ~1,3 MB/s | JVM-Heap-Metriken, Garbage Collection, irgendwann OOM-Kill |
| Kesselkühlung defekt | Dampfdruck über 9,5 bar | `sub.boiler.pressure`, OVERHEAT-Events |
| Leck im Rumpf | Boote sinken | `sub.depth` steigt, `sub.hull` fällt |
| Meeresströmung | Boote treiben ab | `sub.speed` |

**Idee: DB-Verzögerung mit echtem DB-Span.** Heute wartet das Leaderboard per `Thread.sleep` vor dem Insert; im Trace
ist das kein DB-Span. In H2 lässt sich per `CREATE ALIAS pg_sleep FOR "…"` eine Java-Funktion als SQL-Funktion
anlegen. Ein `SELECT pg_sleep(…)` vor dem Insert hält die Verzögerung dann in der Datenbank, und sie ist in SigNoz
wieder als langer DB-Span sichtbar.

## Ideen für Teams

- Dashboard „Brücke“: Tiefe, Druck und Rumpf je Boot, dazu Ereignisse als Zeitreihe
- Alert auf `sub.boiler.pressure > 9.5` und auf p95 von `game.tick.duration > 50 ms`
- SLO: 99 % der Ticks unter 50 ms. Welcher Chaos-Schalter reißt es?
- Kosten pro Gegner und Stunde aus `enemy_ai.llm.cost`. Lokales Modell und Cloud-Modell vergleichen
- Langfuse an LiteLLM hängen und die Qualität der Gegner-Entscheidungen bewerten
- kAgent fragen: „Warum ruckelt das Spiel?“, während Physik-Latenz aktiv ist

## Variante „Slop Arcade“

**Zielbild:** Neben dem U-Boot-Setting eine spielerische Variante: KI-Agenten werfen mit KI-Slop, Menschen werfen mit
Verstand (Geistesblitze). Die Variante ist rein clientseitig (`static/theme.js`) und lässt sich im Spiel und im
Admin-Bereich umschalten (Knopf „Variante“ oder `?theme=slop`, gemerkt im Browser). Server, Mechanik, Gegnernamen,
LLM-Prompt, Metriken und Logs bleiben beim U-Boot.

| Intern (U-Boot) | In der Slop-Variante |
| --- | --- |
| U-Boot (`Sub`) | Figur: Mensch (Spieler, Kolleg:innen-Bots) oder KI-Agent (Gegner) |
| Kraken, Leviathan, Nautilus, Moby, Nessie | SlopGPT, Halluzinator, Buzzwordinator, Copypasta, Promptimus |
| Tiefe, Zerstörungstiefe | Slop-Pegel, „Ab hier versinkt man im Slop“ |
| Dampfdruck, Kessel überhitzt | Denkdruck, „Kopf raucht“ |
| Rumpf | Geduld |
| Torpedo | Geistesblitz (Mensch) bzw. Slop-Wurf (Agent) |
| Kesselkühlung defekt / Leck im Rumpf / Meeresströmung | Kaffeemaschine kaputt / Benachrichtigungsflut / Hype-Welle |

**Offen:** LLM-Prompt in Slop-Fassung (gilt serverweit, wäre also eine Admin-Einstellung statt eines Umschalters pro
Browser); optional lässt das LLM den Slop-Text selbst erzeugen (sichtbar in Langfuse, kostet Tokens, daher
standardmäßig aus).
