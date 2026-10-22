# CLAUDE.md – U-Boot Arcade

Kontext für Claude Code. Sprache im Projekt: Deutsch (UI, Kommentare, Logs, Doku). Code-Bezeichner Englisch.

## Worum es geht

Ein Multiplayer-U-Boot-Arcade-Spiel als „eigene App“ für einen INNOQ-Observability-Workshop
(OpenTelemetry, SigNoz, Langfuse). Ziel: technische Telemetrie (Latenzen, Fehler, JVM, DB) und fachliche
Telemetrie (Tiefe, Dampfdruck, Treffer) erzeugen, Störungen per Chaos-Schalter auslösen und alles
in SigNoz bzw. Langfuse wiederfinden. Das Spiel soll vor dem Workshop fertig sein.

Workshop-Umgebung (vom Veranstalter):
- Pro Person ein Browser-VS-Code. **Kein Docker** – die Services werden direkt als JVM-Prozesse gestartet.
- Gehostet: SigNoz, OpenTelemetry Demo, Langfuse. OTLP-Adressen und Zugangsdaten noch unbekannt.
- LLM-Zugang: Anthropic und „Heribert“ (Art des Endpunkts noch unklar, vermutlich OpenAI-kompatibel).
- Wunsch des Veranstalters: Agenten nicht „durchglühen“ lassen (teuer), lieber gezielt mit Modellen experimentieren.

## Architektur

```
Browser ──WS──► game-server ──HTTP──► physics (zustandslos, skalierbar)
player-swarm ─WS─►  │ 20 Ticks/s
                    ├──► enemy-ai ──► LiteLLM ──► Modell   (Fallback: regelbasierter BotBrain)
                    └──► leaderboard ──JDBC──► Postgres
```

Module (Gradle, Kotlin 2.2.21, Spring Boot 3.5.16, JDK 21):
- `game-core` – reine Spiellogik ohne Frameworks (Model, PhysicsEngine, BotBrain, LlmProtocol) + Tests
- `physics-service` (8081), `game-server` (8080, WebSocket `/ws`, Frontend in `src/main/resources/static`),
  `leaderboard` (8082), `enemy-ai` (8083), `player-swarm` (Lastgenerator, kein Webserver)

Telemetrie: OTel-Java-Agent 2.32.0 zur Laufzeit (Zero-Code für HTTP/JDBC/Logs/JVM), im Code nur
`opentelemetry-api` 1.66.0 über `GlobalOpenTelemetry` (muss zur Agent-Version passen).
Eigene Metriken: siehe README.md. GenAI-Spans/Metriken in `enemy-ai/.../LlmClient.kt` nach GenAI-Semconv.

## Stand der Verifikation

- `game-core`: kompiliert, 16 Tests grün.
- Frontend (index.html, admin.html, game.js): gegen Mock-Server im Headless-Browser getestet, keine JS-Fehler.
- Spring-Services: **noch nie kompiliert oder gestartet** (in der bisherigen Umgebung war Maven Central gesperrt).
  Nur per Code-Review geprüft. Erster Schritt hier: `gradle build` und Fehler beheben.
- docker-compose.yaml valide; Helm-Chart nur mit eigenem Renderer geprüft, nicht mit `helm lint`.

## Offene Aufgaben (Priorität)

1. **Build zum Laufen bringen**: Gradle Wrapper erzeugen (`gradle wrapper --gradle-version 8.14.3`),
   `foojay-resolver-convention` in `settings.gradle.kts` (JDK-21-Toolchain automatisch laden), `./gradlew build`.
2. **Start ohne Docker**: `run-local.sh` / `stop-local.sh` – OTel-Agent einmal laden, bootJars bauen,
   5 Services im Hintergrund mit Ports, Logs nach `logs/`, PIDs merken. Konfiguration über `.env`.
3. **Leaderboard ohne Postgres**: Profil `local` mit H2 (Datei-DB). `pg_sleep`-Chaos dann per `Thread.sleep`
   oder H2-Äquivalent; Schema H2-kompatibel halten (BIGSERIAL/TIMESTAMPTZ anpassen).
4. **Kosten-Bremse für echte LLMs**: Entscheidungsintervall der Gegner-KI bei LLM-Betrieb auf 5–10 s,
   Runaway-Chaos bei Cloud-Modellen deckeln, LiteLLM-Budget (`max_budget`) setzen.
5. **Export an SigNoz und Langfuse**: lokaler OTel Collector (otelcol-contrib-Binary) als Fan-out –
   alles an SigNoz, nur GenAI-Spans (enemy-ai/litellm) an Langfuse.
   Langfuse-OTLP: **nur HTTP** (kein gRPC), `https://<host>/api/public/otel`,
   Header `Authorization=Basic base64(pk-lf-…:sk-lf-…)`, `x-langfuse-ingestion-version=4`.
6. **Langfuse-tauglich machen**: in `LlmClient` `gen_ai.prompt`/`gen_ai.completion` (bzw. `langfuse.observation.input/output`)
   setzen, `langfuse.session.id` pro Spielrunde/Gegner, `gen_ai.usage.cost`.
7. **Modelle**: Anthropic (kleines, günstiges Modell) und Heribert in `deploy/litellm-config.yaml`; LiteLLM ohne Docker
   per `pip install 'litellm[proxy]'`. Alternativ enemy-ai direkt gegen einen OpenAI-kompatiblen Endpunkt.
8. **Optional: Jev (TypeSafe, „System One“-Modell)** als dritte Gegner-KI-Variante, umschaltbar im Admin-Panel
   (regelbasiert / LLM / Jev). Eigene API (`POST https://api.typesafe.ai/v1/systemone`, Route `jev-latest`,
   Felder `model`, `state`, `questions`; Fragetypen Choice/Score/Noul), nicht OpenAI-kompatibel, Early Access
   mit Warteliste. Nur bauen, wenn Zugang vorhanden. Konfidenz pro Entscheidung als Metrik.

## Hinweise

- Bekannte Designentscheidungen: Game-Server bleibt 1 Replica (Spielstand im Speicher). Broadcast läuft
  pro Client über virtuelle Threads und verwirft alte Frames (`game.websocket.frames_dropped`).
- 20 Ticks/s = 20 Traces/s → für gehostetes SigNoz Sampling erwägen (`parentbased_traceidratio`).
- Begleitdokumente liegen im claude.ai-Projekt „observability-workshop-2026“ (README des Spiels, o11y-playground).
