# Qualitätsziele

<!-- ABGELEITET: Aus Code erkannt, bitte verifizieren (Ziele und Reihenfolge) -->

Die folgenden Qualitätsziele haben höchste architektonische Relevanz:

| Priorität | Qualitätsziel | Szenario |
|-----------|--------------|----------|
| 1 | Beobachtbarkeit (Aussagekraft der Telemetrie) | Eine Teilnehmerin findet in SigNoz zu einem Tick den vollständigen Trace (Game-Server → Physik, Gegner-KI → LLM, Leaderboard → DB) und sieht fachliche Werte (`sub.depth`, `sub.boiler.pressure`) neben technischen (Latenz, Heap). |
| 2 | Störbarkeit (reproduzierbares Chaos) | Ein Chaos-Schalter im Admin-Panel wirkt innerhalb weniger Sekunden und erzeugt jedes Mal dasselbe erkennbare Muster (z. B. Physik-Latenz → `game.tick.duration` steigt, Overruns). |
| 3 | Einfache Inbetriebnahme | Lokal, in GitHub Codespaces und im Workshop-VS-Code laufen die Services ohne Docker mit einem JDK ≥ 21 und Umgebungsvariablen; der Build lädt Gradle selbst. Das Spiel läuft auch ohne gestartetes Telemetrie-Backend. Beides ist umgesetzt (`run-local.sh`, ADR-10); im Workshop-VS-Code und in Codespaces noch nicht erprobt. |
| 4 | Kostenkontrolle bei LLMs | Mit Cloud-Modellen bleiben Aufrufe und Kosten begrenzt; Kosten sind als Metrik sichtbar (`enemy_ai.llm.cost`). Die Kostenbremse (Runaway-Deckel, LiteLLM-Budget) ist **geplant**. <!-- TODO: Bitte ergänzen (Budgetgrenze) --> |
| 5 | Flüssiges Spiel | Der Game-Server schafft im Normalbetrieb jeden Tick in 50 ms (`game.tick.overruns` ≈ 0); langsame Clients bremsen andere nicht aus (alte Frames werden verworfen). |

Zielkonflikte:
- **Störbarkeit vs. flüssiges Spiel:** Chaos soll das Spiel spürbar beeinträchtigen, aber nicht dauerhaft unbrauchbar machen
  (z. B. Physik-Latenz auf 1500 ms unter dem 2-s-Timeout gedeckelt).
- **Beobachtbarkeit vs. Kosten:** 20 Traces/s und LLM-Telemetrie erzeugen Datenvolumen bzw. Kosten; Sampling und
  sparsamer LLM-Einsatz wirken dagegen.
- **Einfache Inbetriebnahme vs. Beobachtbarkeit/Störbarkeit:** Die eingebettete H2-Datei-DB (ADR-07) braucht keine
  DB-Infrastruktur, dafür verzögert der Chaos-Schalter „Leaderboard langsam“ den Request per `Thread.sleep` und nicht
  die Datenbank: Im Trace erscheint ein langer HTTP-Span statt eines langen DB-Spans.

- **Spielspaß (Darstellungsvariante) vs. Beobachtbarkeit:** Die Slop-Variante zeigt umgerechnete bzw. qualitative
  Werte und andere Namen als die Telemetrie (ADR-12). Für den Abgleich von Anzeige und Metriken ist die U-Boot-Variante
  gedacht; sie bleibt Standard.

> Ansätze zur Erreichung: [Kapitel 4](../04-Loesungsstrategie/04-01-Strategie.md).
> Detaillierte Szenarien: [Kapitel 10](../10-Qualitaetsanforderungen/).
