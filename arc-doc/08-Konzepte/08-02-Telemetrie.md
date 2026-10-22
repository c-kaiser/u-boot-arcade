# Telemetrie und Instrumentierung

## Problemstellung

Das Spiel existiert, um Telemetrie zu erzeugen: technische (Latenzen, Fehler, JVM, DB) und fachliche (Tiefe,
Dampfdruck, Treffer). Sie muss ohne großen Code-Aufwand vollständig sein und in SigNoz (und GenAI-Anteile in Langfuse)
auffindbar sein.

## Lösung: Agent + API

| Ebene | Quelle | Status |
|---|---|---|
| HTTP-Server/-Client, JDBC, Logs, JVM-Metriken, Kontext-Propagation (`traceparent`) | OTel-Java-Agent 2.32.0 (`-javaagent`, zur Laufzeit) | umgesetzt |
| Eigene Spans und Metriken | nur `opentelemetry-api` 1.66.0 über `GlobalOpenTelemetry` | umgesetzt |
| Export | Agent per OTLP (Compose: gRPC an `OTEL_EXPORTER_OTLP_ENDPOINT`) | umgesetzt |

- Ohne Agent liefert `GlobalOpenTelemetry` eine No-op-Implementierung – der Code läuft, meldet aber nichts.
- Die API-Version ist in `build.gradle.kts` zentral festgelegt (`otelApiVersion`) und muss zur Agent-Version passen
  (Agent 2.32.0 bündelt API 1.66.0). Nur gemeinsam ändern (AGENTS.md).
- Instrumentation-Scopes: `u-boot-arcade.<service>` (z. B. `u-boot-arcade.game-server`).
- Ressourcen-Attribute über Umgebung: `OTEL_SERVICE_NAME` pro Service, `service.namespace=u-boot-arcade`,
  `deployment.environment.name` (docker-compose.yaml).

## Eigene Spans

| Span | Service | Inhalt |
|---|---|---|
| `game.tick` | game-server | 20/s, **eigener Root-Span** (`setNoParent`), Attribute `game.tick`, `game.subs`, `game.players`; Status ERROR, wenn die Physik ausfällt |
| `game.broadcast` | game-server | Kind von `game.tick`, `game.clients`, `game.state.bytes` |
| `physics.simulate`, `chaos latency` | physics | Simulation bzw. künstliche Chaos-Verzögerung |
| `chat <model>` | enemy-ai | GenAI-Client-Span (siehe unten) |

Asynchrone Arbeit (Gegner-Entscheidungen, Leaderboard-Einträge) wird mit `Context.current().wrap(...)` an
Executor-Threads übergeben, damit sie im Trace des auslösenden Ticks landen.

## Eigene Metriken (Auswahl)

<!-- ABGELEITET: Aus Code erkannt (GameTelemetry.kt, EnemyAiApplication.kt, LlmClient.kt, PhysicsApplication.kt, LeaderboardApplication.kt, PlayerSwarmApplication.kt), bitte verifizieren -->

| Bereich | Metriken |
|---|---|
| Tick/Server | `game.tick.duration` (Histogramm, ms), `game.tick.overruns`, `game.physics.failures` (`error.type`), `game.state.size` |
| WebSocket | `game.client.rtt` (`client.kind`), `game.websocket.messages` (`direction`, `message.type`), `game.websocket.frames_dropped`, `game.players.connected` |
| Fachlich | Observable Gauges pro lebendem Boot: `sub.depth`, `sub.speed`, `sub.boiler.pressure`, `sub.boiler.temperature`, `sub.hull`, `sub.score` (`sub.name`, `sub.kind`); `game.subs.alive`; `game.events` (`game.event.type`) |
| Gegner-KI | `game.enemy.decisions`, `game.enemy.decision.latency`; in enemy-ai `enemy_ai.decisions` (`enemy.decision.source`/`outcome`), `enemy_ai.llm.calls_per_decision`, `enemy_ai.llm.cost` (USD) |
| Leaderboard/Physik | `game.leaderboard.dropped` (`reason`), `leaderboard.scores.submitted`, `leaderboard.score`, `physics.events`, `physics.step.entities`, `chaos.injected` (`chaos.kind`) |
| Lastgenerator | `swarm.rtt`, `swarm.state.interval`, `swarm.connects`, `swarm.players.connected` |

Gauges werden beim Export abgelesen (Standard 60 s, in `.env.example` per `OTEL_METRIC_EXPORT_INTERVAL=10000` auf 10 s).
Histogramme setzen eigene Bucket-Grenzen über `setExplicitBucketBoundariesAdvice` (z. B. Tick 1–2500 ms).

## GenAI-Semantic-Conventions (enemy-ai, `LlmClient.kt`)

- Span `chat <model>`, `SpanKind.CLIENT`, Attribute `gen_ai.operation.name=chat`, `gen_ai.provider.name=openai`,
  `gen_ai.request.model`, `gen_ai.request.max_tokens`, `gen_ai.request.temperature`, `gen_ai.response.model`,
  `gen_ai.usage.input_tokens`/`output_tokens`, `gen_ai.response.finish_reasons`, `error.type`, dazu `enemy_ai.llm.cost_usd`.
- Metriken `gen_ai.client.token.usage` (`gen_ai.token.type`) und `gen_ai.client.operation.duration` (s).
- Darunter erzeugt der Agent den HTTP-Span zu LiteLLM und reicht die Trace-ID weiter; LiteLLM exportiert eigene
  Spans (`callbacks: ["otel"]`, OTLP/HTTP).
- `gen_ai.provider.name` ist fest `openai` (Protokoll), auch wenn hinter LiteLLM Anthropic steckt.
  <!-- TODO: Bitte ergänzen – gewollt oder aus gen_ai.response.model ableiten? -->

**Geplant (nicht umgesetzt):** für Langfuse `gen_ai.prompt`/`gen_ai.completion` bzw.
`langfuse.observation.input/output`, `langfuse.session.id` pro Spielrunde/Gegner, `gen_ai.usage.cost`.

## Logs

Spielereignisse als Logs über SLF4J auf Deutsch (z. B. „Kessel von Kraken überhitzt“); der Agent exportiert sie
mit Trace-/Span-ID. Log-Level-Konzept: `warn` für Störungen, `info` für Spielverlauf, `debug` für erwartbare
Einzelfehler (z. B. Senden an einen Client).

## Sampling und Volumen

- 20 Ticks/s ⇒ mindestens 20 Root-Traces/s, plus Physik-, Gegner- und Leaderboard-Spans.
- Standard in Compose und Helm: `parentbased_always_on`. Für gehostetes SigNoz empfohlen (README):
  `OTEL_TRACES_SAMPLER=parentbased_traceidratio`, `OTEL_TRACES_SAMPLER_ARG=0.1`.
- Metriken sind vom Trace-Sampling unabhängig; Tick-Dauer und Fehlerzahlen bleiben vollständig.
- Head-Sampling kann seltene Fehler-Traces verwerfen. <!-- TODO: Bitte ergänzen – Tail-Sampling im geplanten Collector? -->

## Export-Ziele

Die Telemetrie-Betriebsart wird nur in `.env` gewählt (Blöcke in `.env.example`); Code und Startskripte kennen keine
Adressen (ADR-10). Das gilt für `run-local.sh` und Docker Compose; das Helm-Chart exportiert immer per OTLP an `otel.endpoint`.

| Telemetrie-Betriebsart | Ziel | Status |
|---|---|---|
| Ohne Telemetrie (Standard) | kein Export: `OTEL_TRACES_EXPORTER`, `OTEL_METRICS_EXPORTER`, `OTEL_LOGS_EXPORTER` = `none`; Agent instrumentiert weiter | umgesetzt |
| Lokal | SigNoz oder Collector auf dem eigenen Rechner: Exporter `otlp`, `OTEL_EXPORTER_OTLP_PROTOCOL=grpc`, `OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317` (aus Compose `host.docker.internal`); eigenes Compose für die Telemetrie-Services geplant (#5) | umgesetzt |
| Bereitgestellt (z. B. Workshop) | gehostetes SigNoz, Zugang über `OTEL_EXPORTER_OTLP_HEADERS` | umgesetzt, Endpunkte unbekannt |
| Langfuse | lokaler OTel Collector (otelcol-contrib) als Fan-out: alles an SigNoz, GenAI-Spans (enemy-ai/litellm) per **OTLP/HTTP** an `https://<host>/api/public/otel` (Basic-Auth, `x-langfuse-ingestion-version=4`) | geplant, [ADR-09](../09-Entscheidungen/09-09-Collector-Fan-out.md) |

## Geltungsbereich

Alle Spring-Services (game-server, physics, enemy-ai, leaderboard, player-swarm). `game-core` hat keine
OTel-Abhängigkeit.
