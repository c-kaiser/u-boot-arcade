# Qualitätsziele

<!-- ABGELEITET: Aus Code erkannt, bitte verifizieren (Ziele und Reihenfolge) -->

Die folgenden Qualitätsziele haben höchste architektonische Relevanz:

| Priorität | Qualitätsziel | Szenario |
|-----------|--------------|----------|
| 1 | Beobachtbarkeit (Aussagekraft der Telemetrie) | Eine Teilnehmerin findet in SigNoz zu einem Tick den vollständigen Trace (Game-Server → Physik, Gegner-KI → LLM, Leaderboard → DB) und sieht fachliche Werte (`sub.depth`, `sub.boiler.pressure`) neben technischen (Latenz, Heap). |
| 2 | Störbarkeit (reproduzierbares Chaos) | Ein Chaos-Schalter im Admin-Panel wirkt innerhalb weniger Sekunden und erzeugt jedes Mal dasselbe erkennbare Muster (z. B. Physik-Latenz → `game.tick.duration` steigt, Overruns). |
| 3 | Einfache Inbetriebnahme | Im Workshop-VS-Code (ohne Docker) laufen die Services mit einem JDK ≥ 21 und Umgebungsvariablen; der Build lädt Gradle selbst. Start ohne Docker ist **geplant**. |
| 4 | Kostenkontrolle bei LLMs | Mit Cloud-Modellen bleiben Aufrufe und Kosten begrenzt; Kosten sind als Metrik sichtbar (`enemy_ai.llm.cost`). Die Kostenbremse (5–10 s Entscheidungsintervall, Runaway-Deckel, LiteLLM-Budget) ist **geplant**. <!-- TODO: Bitte ergänzen (Budgetgrenze) --> |
| 5 | Flüssiges Spiel | Der Game-Server schafft im Normalbetrieb jeden Tick in 50 ms (`game.tick.overruns` ≈ 0); langsame Clients bremsen andere nicht aus (alte Frames werden verworfen). |

Zielkonflikte:
- **Störbarkeit vs. flüssiges Spiel:** Chaos soll das Spiel spürbar beeinträchtigen, aber nicht dauerhaft unbrauchbar machen
  (z. B. Physik-Latenz auf 1500 ms unter dem 2-s-Timeout gedeckelt).
- **Beobachtbarkeit vs. Kosten:** 20 Traces/s und LLM-Telemetrie erzeugen Datenvolumen bzw. Kosten; Sampling und
  sparsamer LLM-Einsatz wirken dagegen.

> Ansätze zur Erreichung: [Kapitel 4](../04-Loesungsstrategie/04-01-Strategie.md).
> Detaillierte Szenarien: [Kapitel 10](../10-Qualitaetsanforderungen/).
