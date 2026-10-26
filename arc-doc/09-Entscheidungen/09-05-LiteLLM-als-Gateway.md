# ADR-05: LiteLLM als Gateway für die Gegner-KI

**Status:** accepted (umgesetzt in `deploy/litellm-config.yaml` und den Helm-Werten; Workshop-Modelle offen)

**Datum:** 2026-10-22 <!-- ABGELEITET: Datum des Commits, in dem die Entscheidung im Code sichtbar wird -->

## Kontext

Die Gegner-KI soll mit Anthropic und „Heribert“ (Art des Endpunkts unklar, vermutlich
OpenAI-kompatibel) laufen. Der Veranstalter wünscht gezieltes Experimentieren mit Modellen.

## Entscheidung

enemy-ai spricht nur das OpenAI-kompatible Chat-API (`LLM_BASE_URL`, Modellalias `enemy-brain`). Welches echte
Modell dahinter steckt, legt `deploy/litellm-config.yaml` fest (heute `anthropic/claude-haiku-4-5`;
lokale Modelle sind seit ADR-14 ausgeschlossen). LiteLLM exportiert eigene OTel-Spans.

## Konsequenzen

### Positiv
- Modellwechsel ohne Codeänderung; einheitliche Token-/Kosten-Telemetrie.
- Spätere Budgetgrenze zentral möglich (`max_budget`, geplant).

### Negativ
- Zusätzlicher Prozess; seit ADR-11 kein Compose-Profil mehr, ohne Docker per `pip install 'litellm[proxy]'` (geplant, nicht erprobt).
- Alternativ ist enemy-ai direkt gegen einen OpenAI-kompatiblen Endpunkt nutzbar – dann fehlen LiteLLM-Spans/-Budget.
- Heribert noch nicht eingetragen. <!-- TODO: Bitte ergänzen, sobald API-Art bekannt -->
