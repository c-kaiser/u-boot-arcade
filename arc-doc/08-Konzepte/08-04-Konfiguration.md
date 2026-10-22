# Konfiguration

## Problemstellung

Dieselben Artefakte laufen lokal (Docker Compose, SigNoz/Langfuse in Docker), im Workshop (JVM-Prozesse ohne Docker,
gehostete Ziele) und im Kubernetes-Cluster (Helm).

## Lösung

**Alles über Umgebungsvariablen** – der Code kennt keine Telemetrie-Adressen. Spring liest Platzhalter mit
Standardwert aus `application.yaml` (`${VAR:default}`), gebunden an `@ConfigurationProperties` (`GameProperties`,
`LlmProperties`, `swarm.*`). Den OTel-Export konfiguriert ausschließlich der Agent über `OTEL_*`-Variablen.

| Gruppe | Variablen (Standard) |
|---|---|
| Ports | `PORT` (game-server 8080, physics 8081, leaderboard 8082, enemy-ai 8083) |
| Game-Server | `PHYSICS_URL`, `ENEMY_AI_URL`, `LEADERBOARD_URL`, `GAME_TICK_MILLIS` (50), `GAME_BOTS` (3), `GAME_ENEMIES` (2), `GAME_LLM_ENABLED` (false), `GAME_ENEMY_DECISION_EVERY_TICKS` (20) |
| LLM | `LLM_BASE_URL` (`http://litellm:4000/v1`), `LLM_MODEL` (`enemy-brain`), `LLM_API_KEY`, `LLM_TIMEOUT_MS` (4000), `LLM_MAX_TOKENS` (60), `LLM_PRICE_INPUT_PER_MTOK` (3.0), `LLM_PRICE_OUTPUT_PER_MTOK` (15.0) |
| Leaderboard | `DB_URL` (`jdbc:h2:file:./data/leaderboard`), `DB_USER` (`sa`), `DB_PASSWORD` (leer) |
| Schwarm | `GAME_WS_URL`, `SWARM_PLAYERS`, `SWARM_RAMP_UP_MS`, `SWARM_INPUT_INTERVAL_MS`, `SWARM_NAME_PREFIX` |
| Telemetrie | `OTEL_EXPORTER_OTLP_ENDPOINT`, `OTEL_EXPORTER_OTLP_HEADERS`, `OTEL_SERVICE_NAME`, `OTEL_RESOURCE_ATTRIBUTES`, `OTEL_METRIC_EXPORT_INTERVAL`, `OTEL_TRACES_SAMPLER(_ARG)` |

- Vorlage: `.env.example` → `.env` (Compose liest sie; der geplante `run-local.sh` ebenfalls).
- Helm: entsprechende Werte in `values.yaml`, OTel-Umgebung über das Template `uboot.otelEnv`.
- **Zwei Ebenen**: Startwerte aus der Umgebung; Bots, Gegner, LLM-Schalter und Chaos sind zur Laufzeit im
  Admin-Panel änderbar (nur im Speicher, siehe [08-03](08-03-Chaos-und-Stoerungen.md)).
- Welches echte Modell hinter `enemy-brain` steckt, entscheidet nur `deploy/litellm-config.yaml`.

## Offene Punkte

- Workshop-Werte (OTLP-Endpunkte, Ingestion-Keys, Langfuse-Keys, Heribert-Endpunkt) sind unbekannt.
  <!-- TODO: Bitte ergänzen, sobald der Veranstalter sie liefert -->
- Portkonflikt: Game-Server-Standard 8080 = SigNoz-UI-Port; ohne Docker `PORT=8088` setzen (Compose nutzt bereits 8088).
