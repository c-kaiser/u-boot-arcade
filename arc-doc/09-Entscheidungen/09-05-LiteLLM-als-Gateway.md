# ADR-05: LiteLLM als Gateway für die Gegner-KI

**Status:** accepted; das Gateway stellt seit [ADR-15](09-15-LiteLLM-aus-dem-Workshop-Cluster.md) das Workshop-Cluster

**Datum:** 2026-10-22 <!-- ABGELEITET: Datum des Commits, in dem die Entscheidung im Code sichtbar wird -->

## Kontext

Die Gegner-KI soll mit Anthropic und „Heribert“ (Art des Endpunkts unklar, vermutlich
OpenAI-kompatibel) laufen. Der Veranstalter wünscht gezieltes Experimentieren mit Modellen.

## Entscheidung

enemy-ai spricht nur das OpenAI-kompatible Chat-API (`LLM_BASE_URL`, `LLM_MODEL`) und erreicht die Modelle über
LiteLLM. Ursprünglich war eine eigene Instanz mit `deploy/litellm-config.yaml` und dem Alias `enemy-brain` vorgesehen;
seit ADR-15 nutzt enemy-ai das Gateway des Workshop-Clusters, Modell `eu.deepseek-v4.1-flash`. Lokale Modelle sind
seit ADR-14 ausgeschlossen.

## Konsequenzen

### Positiv
- Modellwechsel ohne Codeänderung; einheitliche Token-/Kosten-Telemetrie.
- Budgetgrenze zentral im Gateway möglich – liegt seit ADR-15 beim Betreiber.

### Negativ
- Abhängigkeit vom Gateway; ohne Erreichbarkeit fällt enemy-ai auf `BotBrain` zurück.
- Alternativ ist enemy-ai direkt gegen einen OpenAI-kompatiblen Endpunkt nutzbar – dann fehlen LiteLLM-Spans/-Budget.
- Ob „Heribert“ dieses Gateway ist, ist offen. <!-- TODO: Bitte ergänzen -->
