# LLM-Kostenkontrolle

## Problemstellung

Echte Modelle (über das LiteLLM des Workshop-Clusters) kosten Geld; der Veranstalter wünscht, Agenten nicht „durchglühen“ zu lassen.
Die Gegner-KI fragt dagegen regelmäßig, und das Chaos `aiRunaway` vervielfacht die Aufrufe absichtlich.

## Heute umgesetzt

- **Aus per Default**: `GAME_LLM_ENABLED=false`; Gegner nutzen den regelbasierten `BotBrain`. Einschalten im Admin-Panel.
- **Kleine Antworten**: `max_tokens` 60, `temperature` 0.3, Antwortformat kompaktes JSON (`LlmProtocol`).
- **Begrenzte Parallelität**: max. 5 Gegner (`ENEMY_NAMES`), eine laufende Anfrage pro Gegner, 4 Threads.
- **Takt**: Entscheidung alle `GAME_ENEMY_DECISION_EVERY_TICKS`=20 Ticks (≈ 1 s bei 50 ms) pro Gegner – mit und
  ohne LLM gleich. Ein längeres Intervall nur bei LLM-Betrieb ist bewusst nicht vorgesehen (entschieden 2026-10-26);
  die Kosten begrenzen Runaway-Deckel und Budget. Wer seltener fragen will, setzt den Wert per Konfiguration.
- **Messen**: `gen_ai.client.token.usage`, `enemy_ai.llm.cost` (aus Tokens × `LLM_PRICE_*_PER_MTOK`),
  `enemy_ai.llm.calls_per_decision`; LiteLLM rechnet zusätzlich selbst (Preise beim Betreiber des Gateways).
- **Kein lokales Modell** (ADR-14): `LLM_MODEL` ist `eu.deepseek-v4.1-flash` aus dem Workshop-Gateway (ADR-15);
  jeder LLM-Test kostet Geld, deshalb nur kurz einschalten.
- **Leitplanke für Agenten** (AGENTS.md): keine Schleifen/Lasttests/Runaway gegen Cloud-Modelle ohne Rückfrage.

## Geplant

- Runaway-Chaos bei Cloud-Modellen deckeln.
- Budget: im Workshop-Gateway liegt es beim Betreiber (ob gesetzt, ist offen); eigener Deckel in enemy-ai.

<!-- TODO: Bitte ergänzen – Kostenrahmen pro Teilnehmer/Workshop, Preise für `eu.deepseek-v4.1-flash` -->

## Größenordnung (Rechnung, keine Messung)

Bei 2 Gegnern und 1 Entscheidung/s ≈ 7 200 Aufrufe/h; mit `aiRunaway` × 8 und wachsendem Kontext.
Mit `GAME_ENEMY_DECISION_EVERY_TICKS`=200 (10 s) wären es ≈ 720 Aufrufe/h.
