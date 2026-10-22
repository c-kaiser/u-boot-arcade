# Risiken und technische Schulden

Bewertung qualitativ (Wahrscheinlichkeit × Auswirkung), sortiert nach Priorität. Stand: Branch `docs/agents-readme-arc42`.

## Risiken

| ID | Risiko | Wahrsch. | Auswirkung | Maßnahme |
|---|---|---|---|---|
| R1 | **Spring-Services nie gebaut/gestartet** – Kompilier-, Konfigurations- oder Laufzeitfehler tauchen erst spät auf | hoch | hoch | `./gradlew build` (läuft), danach alle Services mit Agent starten und Smoke-Test (Spiel, Admin, Traces) |
| R2 | **Unbekannte Workshop-Endpunkte** (OTLP SigNoz, Langfuse, Zugangsdaten) – Export-Konfiguration nicht vorab testbar | hoch | hoch | Beim Veranstalter anfragen; alles über `.env`; lokal mit SigNoz/Langfuse in Docker proben |
| R3 | **LLM-Kosten** – Entscheidung ≈ 1/s pro Gegner, Runaway × 8 mit wachsendem Kontext, kein Budget-Limit | mittel | hoch | Intervall 5–10 s bei LLM, Runaway deckeln, LiteLLM `max_budget`; Default `GAME_LLM_ENABLED=false` (umgesetzt) |
| R4 | **Kein Start ohne Docker** – `run-local.sh`, H2-Profil, Collector-Binary fehlen | hoch | hoch | Offene Aufgaben TD1–TD3 vor dem Workshop umsetzen und im Browser-VS-Code proben |
| R5 | **Telemetrie-Volumen** – 20 Root-Traces/s pro Instanz × Teilnehmende bei gehostetem SigNoz; Default `parentbased_always_on` | mittel | mittel | `parentbased_traceidratio` 0.1 empfehlen/vorbelegen; Metriken bleiben vollständig |
| R6 | **Heribert-API unklar** (OpenAI-kompatibel?) | mittel | mittel | Klären; sonst nur Anthropic/lokal; enemy-ai fällt bei Fehlern auf `BotBrain` zurück |
| R7 | **Portkonflikt 8080** zwischen Game-Server-Default und SigNoz-UI beim Start ohne Docker | hoch | niedrig | `PORT=8088` im Start-Skript/`.env` setzen |
| R8 | **Netzwerkzugriff im Workshop** auf `services.gradle.org`, Maven Central, `api.foojay.io`, GitHub (Agent-Download), PyPI (LiteLLM) | mittel | hoch | Vorab im Workshop-VS-Code testen; ggf. Artefakte vorab bereitstellen <!-- TODO: Bitte ergänzen --> |
| R9 | **Agent-/API-Versionsdrift** – andere Agent-Version als 2.32.0 passt nicht zu API 1.66.0 | niedrig | mittel | Version im Start-Skript fest pinnen, nur gemeinsam ändern |
| R10 | **Helm-Chart nie mit `helm lint`/echtem Cluster geprüft** | mittel | niedrig (Workshop ohne Cluster) | `helm lint` / `helm template` in CI |
| R11 | **Game-Server Single Point of Failure**, Zustand nur im Speicher (ADR-03) | niedrig | mittel | Akzeptiert; Neustart = neue Runde |
| R12 | **Admin-API ohne Authentifizierung**, WebSocket erlaubt alle Origins | niedrig | niedrig | Akzeptabel bei Instanz pro Person; vor öffentlichem Betrieb absichern |

## Technische Schulden

| ID | Schuld | Bereich | Aufwand | Priorität |
|---|---|---|---|---|
| TD1 | `run-local.sh`/`stop-local.sh` fehlen (Agent laden, bootJars, 5 Services, Logs, PIDs, `.env`) | Betrieb | <!-- TODO --> | hoch |
| TD2 | Leaderboard-Profil `local` mit H2 fehlt; `schema.sql` nutzt `BIGSERIAL`/`TIMESTAMPTZ` | leaderboard | <!-- TODO --> | hoch |
| TD3 | `pg_sleep`-Chaos nicht H2-kompatibel | leaderboard, Chaos | <!-- TODO --> | hoch |
| TD4 | Kosten-Bremse fehlt (Intervall bei LLM, Runaway-Deckel, `max_budget`) | game-server, enemy-ai, LiteLLM | <!-- TODO --> | hoch |
| TD5 | Collector-Fan-out SigNoz/Langfuse fehlt (ADR-09) | Betrieb/Telemetrie | <!-- TODO --> | hoch |
| TD6 | Langfuse-Attribute fehlen (`gen_ai.prompt`/`completion` bzw. `langfuse.observation.*`, `langfuse.session.id`, `gen_ai.usage.cost`) | enemy-ai `LlmClient` | <!-- TODO --> | mittel |
| TD7 | Anthropic-Modell und Heribert nicht final in `litellm-config.yaml`; LiteLLM ohne Docker nicht erprobt | deploy | <!-- TODO --> | mittel |
| TD8 | Chaos pro U-Boot auf Skala (Zielbild) nicht umgesetzt; heute nur globale Schalter | game-server, UI | <!-- TODO --> | mittel |
| TD9 | Keine Tests für Spring-Services (nur `game-core`) | alle Services | <!-- TODO --> | mittel |
| TD10 | `gen_ai.provider.name` fest `openai`, auch bei Anthropic hinter LiteLLM | enemy-ai | gering | niedrig |
| TD11 | Optional: Jev (TypeSafe „System One“) als dritte Gegner-KI – nur bei Zugang | enemy-ai | <!-- TODO --> | niedrig |

<!-- TODO: Bitte ergänzen – Verantwortliche und Termine (vor dem Workshop) -->
