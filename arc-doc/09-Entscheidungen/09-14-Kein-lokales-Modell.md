# ADR-14: Kein lokales Modell für die Gegner-KI

**Status:** accepted

**Datum:** 2026-10-26

## Kontext

Bisher war für die Gegner-KI ein lokales Modell vorgesehen (Ollama mit `qwen2.5:3b` hinter dem Alias `enemy-brain`),
damit LLM-Experimente nichts kosten. Ein Cloud-Modell (Anthropic) war nur als Alternative eingetragen
(`enemy-brain-cloud`). Auf den verfügbaren Rechnern fehlt der Arbeitsspeicher, um ein lokales Modell neben der
Anwendung und den Telemetrie-Services (`deploy/telemetry`) zu betreiben.

## Entscheidung

- Die Gegner-KI nutzt **kein lokales Modell**. Ollama entfällt aus Konfiguration und Dokumentation.
- Der Alias `enemy-brain` in `deploy/litellm-config.yaml` zeigt direkt auf ein günstiges Cloud-Modell
  (heute `anthropic/claude-haiku-4-5`); der Zusatz-Alias `enemy-brain-cloud` entfällt.
- LiteLLM bleibt als Gateway (ADR-05). Cloud-Modelle werden nur kurz für Tests eingeschaltet und danach wieder
  abgeschaltet; Standard bleibt `GAME_LLM_ENABLED=false`.

### Betrachtete Alternativen

- **Ollama behalten:** kostenlos, aber auf der vorhandenen Hardware nicht lauffähig. Verworfen.
- **Ollama als dokumentierte Option behalten:** Für einen Pfad, den niemand ausprobieren kann, müssten Konfiguration
  und Doku weiter gepflegt werden. Verworfen.

## Konsequenzen

### Positiv
- Weniger Komponenten; kein zusätzlicher Prozess und kein Modell-Download.
- Echte Preise und echtes Antwortverhalten in der Telemetrie statt fiktiver Kosten.

### Negativ
- Jeder LLM-Test kostet Geld. Die Kostenbremse (TD4: Runaway-Deckel, Budget) muss vor dem ersten Cloud-Test stehen.
- Ein `ANTHROPIC_API_KEY` ist für jeden LLM-Test nötig.

### Neutral
- enemy-ai bleibt unverändert; der Wechsel liegt allein in `deploy/litellm-config.yaml` (QS-10).
