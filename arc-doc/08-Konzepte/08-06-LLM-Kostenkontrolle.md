# LLM-Kostenkontrolle

## Problemstellung

Echte Modelle (Anthropic, „Heribert“) kosten Geld; der Veranstalter wünscht, Agenten nicht „durchglühen“ zu lassen.
Die Gegner-KI fragt dagegen regelmäßig, und das Chaos `aiRunaway` vervielfacht die Aufrufe absichtlich.

## Heute umgesetzt

- **Aus per Default**: `GAME_LLM_ENABLED=false`; Gegner nutzen den regelbasierten `BotBrain`. Einschalten im Admin-Panel.
- **Kleine Antworten**: `max_tokens` 60, `temperature` 0.3, Antwortformat kompaktes JSON (`LlmProtocol`).
- **Begrenzte Parallelität**: max. 5 Gegner (`ENEMY_NAMES`), eine laufende Anfrage pro Gegner, 4 Threads.
- **Takt**: Entscheidung alle `GAME_ENEMY_DECISION_EVERY_TICKS`=20 Ticks (≈ 1 s bei 50 ms) pro Gegner.
- **Messen**: `gen_ai.client.token.usage`, `enemy_ai.llm.cost` (aus Tokens × `LLM_PRICE_*_PER_MTOK`),
  `enemy_ai.llm.calls_per_decision`; LiteLLM rechnet zusätzlich selbst (Preise in `litellm-config.yaml`).
- **Lokal kostenlos**: Compose-Profil `llm` mit Ollama (`qwen2.5:3b`); fiktive Preise, damit Dashboards etwas zeigen.
- **Leitplanke für Agenten** (AGENTS.md): keine Schleifen/Lasttests/Runaway gegen Cloud-Modelle ohne Rückfrage.

## Geplant

- Entscheidungsintervall bei LLM-Betrieb auf 5–10 s (100–200 Ticks) anheben.
- Runaway-Chaos bei Cloud-Modellen deckeln.
- LiteLLM-Budget (`max_budget`) setzen.

<!-- TODO: Bitte ergänzen – Kostenrahmen pro Teilnehmer/Workshop, gewähltes Anthropic-Modell, Preise für Heribert -->

## Größenordnung (Rechnung, keine Messung)

Bei 2 Gegnern und 1 Entscheidung/s ≈ 7 200 Aufrufe/h; mit `aiRunaway` × 8 und wachsendem Kontext.
Mit Intervall 10 s ≈ 720 Aufrufe/h.
