# Risiken und technische Schulden

Bewertung qualitativ (Wahrscheinlichkeit × Auswirkung), sortiert nach Priorität. Stand: `main` nach PR #7 (2026-10-23).

## Risiken

| ID | Risiko | Wahrsch. | Auswirkung | Maßnahme |
|---|---|---|---|---|
| R1 | **Spring-Services nur teilweise erprobt** – per `run-local.sh` mit Agent gestartet (Spiel, Leaderboard, Schwarm, Export an einen Collector geprüft); Helm und der LLM-Pfad sind noch nicht gelaufen | mittel | hoch | Helm (falls Workshop-Cluster) und LLM-Pfad (lokales Modell) starten, Smoke-Test |
| R2 | **Unbekannte Workshop-Endpunkte** (OTLP SigNoz, Langfuse, Zugangsdaten) – Export-Konfiguration nicht vorab testbar | hoch | hoch | Beim Veranstalter anfragen; alles über `.env`; lokal mit SigNoz/Langfuse in Docker proben |
| R3 | **LLM-Kosten** – Entscheidung ≈ 1/s pro Gegner, Runaway × 8 mit wachsendem Kontext, kein Budget-Limit | mittel | hoch | Intervall 5–10 s bei LLM, Runaway deckeln, LiteLLM `max_budget`; Default `GAME_LLM_ENABLED=false` (umgesetzt) |
| R4 | **Start ohne Docker im Workshop nicht erprobt** – `run-local.sh` ist umgesetzt (TD1), aber nur lokal/in der Sandbox geprüft; das Collector-Binary für den Fan-out fehlt noch | mittel | hoch | `run-local.sh` in Codespaces und im Browser-VS-Code proben; TD5 vor dem Workshop umsetzen |
| R5 | **Telemetrie-Volumen** – 20 Root-Traces/s pro Instanz × Teilnehmende bei gehostetem SigNoz; Default `parentbased_always_on` | mittel | mittel | `parentbased_traceidratio` 0.1 empfehlen/vorbelegen; Metriken bleiben vollständig |
| R6 | **Heribert-API unklar** (OpenAI-kompatibel?) | mittel | mittel | Klären; sonst nur Anthropic/lokal; enemy-ai fällt bei Fehlern auf `BotBrain` zurück |
| R7 | ~~**Portkonflikt 8080** zwischen Game-Server-Default und SigNoz-UI beim Start ohne Docker~~ – erledigt: `run-local.sh` startet den Game-Server auf `GAME_PORT` (8088) | – | – | – |
| R8 | **Netzwerkzugriff im Workshop** auf `services.gradle.org`, `plugins.gradle.org`, Maven Central, GitHub (Agent-Download), PyPI (LiteLLM) | mittel | hoch | Vorab im Workshop-VS-Code testen; ggf. Artefakte vorab bereitstellen <!-- TODO: Bitte ergänzen --> |
| R9 | **Agent-/API-Versionsdrift** – andere Agent-Version als 2.32.0 passt nicht zu API 1.66.0 | niedrig | mittel | Version in `Dockerfile` und `run-local.sh` gepinnt; mit `otelApiVersion` in `build.gradle.kts` nur gemeinsam ändern (AGENTS.md) |
| R10 | **Helm-Chart nie mit `helm lint`/echtem Cluster geprüft** | mittel | niedrig (Workshop ohne Cluster) | `helm lint` / `helm template` in CI |
| R11 | **Game-Server Single Point of Failure**, Zustand nur im Speicher (ADR-03) | niedrig | mittel | Akzeptiert; Neustart = neue Runde |
| R14 | ~~**Gemeinsame `.env` für `run-local.sh` und Compose** (`localhost` vs. `host.docker.internal`)~~ – erledigt: kein Compose für die Anwendung (ADR-11) | – | – | – |
| R12 | **Admin-API ohne Authentifizierung**, WebSocket erlaubt alle Origins | niedrig | niedrig | Akzeptabel bei Instanz pro Person; vor öffentlichem Betrieb absichern |
| R13 | **Leaderboard nicht skalierbar, Daten im Helm-Chart flüchtig** – eingebettete H2-Datei pro Instanz (ADR-07) | niedrig | niedrig | Akzeptiert; `leaderboard.replicas` bleibt 1, bei Bedarf PersistentVolumeClaim statt `emptyDir`, dann mit Update-Strategie `Recreate` (sonst öffnen zwei Pods dieselbe H2-Datei) |

## Technische Schulden

| ID | Schuld | Bereich | Aufwand | Priorität |
|---|---|---|---|---|
| TD1 | ~~`run-local.sh`/`stop-local.sh` fehlen~~ – erledigt (#3, 07-02) | Betrieb | – | – |
| TD2 | ~~Leaderboard-Profil `local` mit H2 fehlt~~ – erledigt: H2 in allen Verteilungsvarianten (ADR-07) | leaderboard | – | – |
| TD3 | ~~`pg_sleep`-Chaos nicht H2-kompatibel~~ – erledigt: `Thread.sleep` (ADR-07) | leaderboard, Chaos | – | – |
| TD4 | Kosten-Bremse fehlt (Intervall bei LLM, Runaway-Deckel, `max_budget`) | game-server, enemy-ai, LiteLLM | <!-- TODO --> | hoch |
| TD5 | Collector-Fan-out SigNoz/Langfuse fehlt (ADR-09) | Betrieb/Telemetrie | <!-- TODO --> | hoch |
| TD6 | Langfuse-Attribute fehlen (`gen_ai.prompt`/`completion` bzw. `langfuse.observation.*`, `langfuse.session.id`, `gen_ai.usage.cost`) | enemy-ai `LlmClient` | <!-- TODO --> | mittel |
| TD7 | Anthropic-Modell und Heribert nicht final in `litellm-config.yaml`; LiteLLM ohne Docker nicht erprobt | deploy | <!-- TODO --> | mittel |
| TD8 | Chaos pro U-Boot auf Skala (Zielbild) nicht umgesetzt; heute nur globale Schalter | game-server, UI | <!-- TODO --> | mittel |
| TD9 | Keine Tests für Spring-Services (nur `game-core`) | alle Services | <!-- TODO --> | mittel |
| TD10 | `gen_ai.provider.name` fest `openai`, auch bei Anthropic hinter LiteLLM | enemy-ai | gering | niedrig |
| TD11 | Optional: Jev (TypeSafe „System One“) als dritte Gegner-KI – nur bei Zugang | enemy-ai | <!-- TODO --> | niedrig |
| TD12 | Darstellungsvarianten (ADR-12): doppelte Zeichenlogik in `game.js`, Zuordnung in `theme.js` an Server-Texte (Gegnernamen, Chaos-Bezeichnungen) gekoppelt, keine Frontend-Tests | game-server/Frontend | gering | niedrig |

<!-- TODO: Bitte ergänzen – Verantwortliche und Termine (vor dem Workshop) -->
