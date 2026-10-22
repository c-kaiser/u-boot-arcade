# U-Boot Arcade

Ein kleines Multiplayer-Arcade-Spiel mit U-Booten. Gebaut als „eigene App“ für einen Workshop zu Observability
mit OpenTelemetry, SigNoz und Langfuse: Es erzeugt technische Telemetrie (Latenzen, Fehler, JVM, DB) und fachliche
Telemetrie (Tiefe, Dampfdruck, Treffer), und es lässt sich gezielt stören, um die Folgen in SigNoz und Langfuse wiederzufinden.

## Architektur

Die Architektur ist in [arc-doc/](arc-doc/00-Ueberblick/00-00-Overview.md) nach arc42 beschrieben. Guter Einstieg:

- [Technischer Kontext](arc-doc/03-Kontextabgrenzung/03-02-Technischer-Kontext.md) – Services, Kanäle, Telemetrie-Ziele
- [Bausteinsicht Ebene 1](arc-doc/05-Bausteinsicht/05-01-Ebene-1.md) – Services und Module im Überblick
- [Verteilungssicht](arc-doc/07-Verteilungssicht/) – Betrieb lokal, mit Docker Compose und in Kubernetes

## Voraussetzungen und Toolchain

Benötigt wird nur **ein JDK ab Version 21** auf dem `PATH`. Der **Gradle Wrapper** (`./gradlew`) lädt die passende
Gradle-Version selbst. Kompiliert wird immer für Java 21 – auch mit einem neueren JDK.

| Umgebung | Einrichtung |
| --- | --- |
| GitHub Codespaces / Dev Container | Nichts zu tun – [.devcontainer/devcontainer.json](.devcontainer/devcontainer.json) bringt JDK 21 und Docker mit |
| Workshop-VS-Code | Vorhandenes JDK genügt (`java -version` ≥ 21) |
| Lokal (macOS/Linux) | z. B. [SDKMAN!](https://sdkman.io/): `sdk install java 21-tem` – oder ein JDK aus dem Paketmanager |

## Bauen und Testen

```bash
./gradlew build                 # alles kompilieren und testen
./gradlew :game-core:test       # nur die Spiellogik
./gradlew bootJar               # ausführbare JARs unter <modul>/build/libs/
```

## Starten

### Ohne Docker (Workshop-Umgebung) – geplant

Im Workshop gibt es kein Docker; die Services laufen direkt als JVM-Prozesse mit dem OTel-Java-Agent.
Dafür entstehen `run-local.sh`/`stop-local.sh` (Konfiguration über `.env`, Logs nach `logs/`). Das Leaderboard braucht
keine externe Datenbank (eingebettete H2-Datei-DB unter `data/`). Bis dahin: Docker Compose.

### Mit Docker Compose

```bash
cp .env.example .env
docker compose up --build                  # Spiel, 3 Bots, 2 regelbasierte Gegner
docker compose --profile swarm up --build  # + 10 Schwarm-Spieler (WebSocket-Last)
docker compose --profile llm up --build    # + LiteLLM und Ollama (qwen2.5:3b) für die Gegner-KI
```

- Spiel: http://localhost:8088 (← → Fahrt, ↑ ↓ Tauchen, Leertaste Torpedo)
- Admin: http://localhost:8088/admin.html (dort auch „LLM für Gegner verwenden“ einschalten)

### Im Kubernetes-Cluster (Helm)

```bash
deploy/build-images.sh ghcr.io/<org> 1.0.0     # baut und pusht 5 Images
helm upgrade --install u-boot deploy/helm/u-boot-arcade -n u-boot --create-namespace \
  --set image.registry=ghcr.io/<org> \
  --set otel.endpoint=http://<signoz-collector-service>.<namespace>.svc.cluster.local:4317
kubectl -n u-boot port-forward svc/u-boot-game-server 8088:80
```

Mehr Last: `kubectl -n u-boot scale deploy/u-boot-player-swarm --replicas=5`.
Physik skalieren: `--set physics.replicas=4`. Der Game-Server bleibt bei einer Replica, weil der Spielstand im Speicher liegt.

## Telemetrie-Ziele konfigurieren

Wohin die Telemetrie geht, wird nur über Umgebungsvariablen (`.env`) festgelegt – der Code kennt keine Adressen.
Zwei Betriebsarten sind vorgesehen:

| Betriebsart | SigNoz / OTLP | Langfuse | LLM |
| --- | --- | --- | --- |
| **Workshop** | gehostetes SigNoz des Veranstalters | gehostetes Langfuse | Anthropic oder Heribert über LiteLLM |
| **Lokal** | SigNoz in Docker, z. B. aus dem `o11y-playground` | Langfuse in Docker | lokales Modell (Ollama) oder Anthropic |

| Variable | Bedeutung |
| --- | --- |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | OTLP-Ziel (SigNoz oder lokaler Collector), z. B. `http://localhost:4317` |
| `OTEL_EXPORTER_OTLP_HEADERS` | Zugangsdaten für gehostete Ziele, z. B. `signoz-ingestion-key=…` |
| `OTEL_TRACES_SAMPLER`, `OTEL_TRACES_SAMPLER_ARG` | Sampling – bei 20 Traces/s für gehostetes SigNoz empfohlen: `parentbased_traceidratio`, `0.1` |
| `LLM_BASE_URL`, `LLM_MODEL`, `LLM_API_KEY` | OpenAI-kompatibler Endpunkt für die Gegner-KI (LiteLLM) |

Langfuse nimmt OTLP **nur per HTTP** an (`https://<host>/api/public/otel`, Basic-Auth mit Public/Secret Key).
Geplant ist ein lokaler OpenTelemetry Collector als Verteiler: alles an SigNoz, nur die GenAI-Spans an Langfuse.

Achtung beim lokalen Start ohne Docker: Der Game-Server nutzt standardmäßig Port 8080 – genau wie die SigNoz-UI.
Einen der beiden umlegen (`PORT=8088` für den Game-Server).

## Was man beobachten kann

### Traces
- **`game.tick`** (20 pro Sekunde): Physik-Aufruf, `game.broadcast`, asynchron die Gegner-Entscheidungen
  und Leaderboard-Einträge (`INSERT` als DB-Span; die Chaos-Verzögerung verlängert den HTTP-Span `POST /api/scores`)
- **`chat enemy-brain`** in `enemy-ai`: GenAI-Span mit `gen_ai.usage.input_tokens`/`output_tokens` und Kosten.
  Darunter der HTTP-Span zu LiteLLM und dessen eigene Spans, die Trace-ID wird durchgereicht.

### Eigene Metriken (Auswahl)

| Metrik | Bedeutung |
| --- | --- |
| `game.tick.duration`, `game.tick.overruns` | Schafft der Server seine 50 ms pro Tick? |
| `game.client.rtt` (`client.kind`) | WebSocket-Round-Trip aus Browser und Schwarm |
| `swarm.state.interval` | Abstand zwischen Updates beim Client (Ruckeln) |
| `game.websocket.frames_dropped` | Updates, die ein langsamer Client nicht abgenommen hat |
| `game.physics.failures` | Physik-Aufrufe, die fehlgeschlagen sind (Welt friert ein) |
| `game.enemy.decision.latency` | Wie lange die Gegner-KI für eine Entscheidung braucht |
| `sub.depth`, `sub.speed`, `sub.boiler.pressure`, `sub.boiler.temperature`, `sub.hull` | Fachliche Werte pro Boot (`sub.name`, `sub.kind`) |
| `game.events` (`game.event.type`) | HIT, KILL, DESTROYED, OVERHEAT, CRUSH_DEPTH, ... |
| `gen_ai.client.token.usage`, `gen_ai.client.operation.duration` | Token-Verbrauch und Dauer der LLM-Aufrufe (GenAI-Semantic-Conventions) |
| `enemy_ai.llm.cost` | Geschätzte Kosten in USD |
| `enemy_ai.decisions` (`enemy.decision.source`/`outcome`) | LLM oder Fallback? ok, timeout, parse_error, ... |
| `enemy_ai.llm.calls_per_decision` | Steigt bei der KI-Endlosschleife |
| `leaderboard.score`, `game.leaderboard.dropped` | Punkte pro Runde, Rückstau beim Leaderboard |

Dazu kommen JVM-, HTTP- und DB-Pool-Metriken vom Agent.

### Logs
Spielereignisse als strukturierte Logs mit Trace-ID, zum Beispiel „Kessel von Kraken überhitzt“ oder
„Christian (PLAYER) zerstört durch torpedo von Kraken“.

## Störungen (Chaos)

Im Admin-Panel (`admin.html`) lassen sich Störungen global einschalten, etwa Physik-Latenz, Fehlerraten, eine langsame
Leaderboard-DB oder eine KI-Endlosschleife. Die Schalter mit ihren Auswirkungen und das Zielbild „Störung pro U-Boot“
stehen in [IDEEN.md](IDEEN.md).

## Projektstruktur

```
game-core/        reine Spiellogik (Physik, Bot-KI, LLM-Prompt/Parsing) + Tests, ohne Frameworks
physics-service/  POST /api/step – zustandslose Physik
game-server/      Tick-Loop, WebSocket /ws, Admin-API, Frontend (src/main/resources/static)
enemy-ai/         POST /api/decide – LLM über LiteLLM, sonst regelbasiert
leaderboard/      POST /api/scores, GET /api/scores/top – H2-Datei-DB über JDBC
player-swarm/     Lastgenerator mit echten WebSocket-Clients
deploy/           LiteLLM-Konfiguration, Helm-Chart, Build-Skript
arc-doc/          Architekturdokumentation nach arc42
IDEEN.md          Ideen: Störungen (Chaos), Ideen für Teams
.agents/          arc42agentic: Agenten und Skills zum Schreiben und Reviewen der arc42-Doku
```

## Mitarbeit

- **Sprache:** Deutsch für UI, Kommentare, Logs und Dokumentation. Code-Bezeichner auf Englisch.
- **Branches:** Nicht direkt auf `main` arbeiten. Jede Änderung auf einem eigenen Branch, Zusammenführen per Pull Request.
- **Aufgaben** werden als GitHub Issues geführt.
- Anweisungen für Coding-Agenten stehen in [AGENTS.md](AGENTS.md).

## Status

- `./gradlew build` läuft durch: alle Module kompilieren, 16 Tests in `game-core` grün.
- Frontend (`index.html`, `admin.html`, `game.js`) gegen einen Mock-Server im Headless-Browser getestet.
- Die Spring-Services wurden bisher **nie gestartet**.
- Helm-Chart nur mit eigenem Renderer geprüft, nicht mit `helm lint`.
