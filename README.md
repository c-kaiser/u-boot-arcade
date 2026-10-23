# U-Boot Arcade

Ein kleines Multiplayer-Arcade-Spiel mit U-Booten. Gebaut als „eigene App“ für einen Workshop zu Observability
mit OpenTelemetry, SigNoz und Langfuse: Es erzeugt technische Telemetrie (Latenzen, Fehler, JVM, DB) und fachliche
Telemetrie (Tiefe, Dampfdruck, Treffer), und es lässt sich gezielt stören, um die Folgen in SigNoz und Langfuse wiederzufinden.

## Architektur

Die Architektur ist in [arc-doc/](arc-doc/00-Ueberblick/00-00-Overview.md) nach arc42 beschrieben. Guter Einstieg:

- [Technischer Kontext](arc-doc/03-Kontextabgrenzung/03-02-Technischer-Kontext.md) – Services, Kanäle, Telemetrie-Ziele
- [Bausteinsicht Ebene 1](arc-doc/05-Bausteinsicht/05-01-Ebene-1.md) – Services und Module im Überblick
- [Verteilungssicht](arc-doc/07-Verteilungssicht/) – Betrieb ohne Docker (lokal, Codespaces) und in Kubernetes

## Voraussetzungen und Toolchain

Benötigt wird nur **ein JDK ab Version 21** auf dem `PATH` (und `curl` für den Start ohne Docker). Der **Gradle Wrapper** (`./gradlew`) lädt die passende
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

### Lokal und in Codespaces (ohne Docker)

Die Services laufen direkt als JVM-Prozesse mit dem OTel-Java-Agent – ohne Docker und ohne weitere Infrastruktur.

```bash
cp .env.example .env     # Standard: ohne Telemetrie, regelbasierte Gegner
./run-local.sh           # baut, startet physics, leaderboard, enemy-ai, game-server
./run-local.sh --swarm   # zusätzlich Schwarm-Spieler (WebSocket-Last)
./stop-local.sh          # beendet alle Services
```

- Spiel: http://localhost:8088 (← → Fahrt, ↑ ↓ Tauchen, Leertaste Torpedo), in Codespaces über die Port-Weiterleitung
- Admin: http://localhost:8088/admin.html (dort auch „LLM für Gegner verwenden“ einschalten)
- Darstellung: Knopf „Variante“ oder `?theme=slop` wechselt zur Slop-Variante (KI-Agenten werfen mit Slop, Menschen
  mit Verstand); nur Anzeige, Spiel und Telemetrie bleiben gleich (ADR-12)
- Logs unter `logs/<service>.log`, PID-Dateien unter `.run/`, Leaderboard-Datenbank (H2) unter `data/`
- Der OTel-Java-Agent wird beim ersten Start nach `.otel/` geladen (braucht Zugriff auf GitHub-Releases)
- Speicher pro JVM über `JAVA_OPTS` (Standard `-Xmx256m`), andere Konfigurationsdatei per `ENV_FILE=… ./run-local.sh`
- Gegner-KI mit LLM: eine LiteLLM-Instanz mit [deploy/litellm-config.yaml](deploy/litellm-config.yaml) starten
  (z. B. `pip install 'litellm[proxy]'`, `litellm --config deploy/litellm-config.yaml --port 4000`) und `LLM_*` in `.env`
  setzen. Echte Cloud-Modelle kosten Geld.

Ein Docker Compose für die Anwendung gibt es bewusst nicht (ADR-11).

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

Wohin die Telemetrie geht, wird nur über Umgebungsvariablen (`.env`) festgelegt – weder Code noch Startskripte kennen
Adressen. Das Spiel läuft auch ganz ohne Telemetrie-Backend. `.env.example` enthält für jede Betriebsart einen Block;
genau einer ist aktiv:

| Betriebsart | Ziel | Einstellungen |
| --- | --- | --- |
| **Ohne Telemetrie** (Standard) | kein Export – der Agent instrumentiert, sendet aber nichts | `OTEL_TRACES_EXPORTER`, `OTEL_METRICS_EXPORTER`, `OTEL_LOGS_EXPORTER` = `none` |
| **Lokal** | SigNoz oder Collector auf dem eigenen Rechner, z. B. aus dem `o11y-playground` | Exporter `otlp`, `OTEL_EXPORTER_OTLP_PROTOCOL=grpc`, `OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317` |
| **Bereitgestellt** | gehostetes SigNoz (z. B. im Workshop) | wie lokal, dazu Endpunkt des Anbieters und `OTEL_EXPORTER_OTLP_HEADERS` |

Ist ein Ziel gesetzt, aber nicht erreichbar, läuft das Spiel weiter; der Agent loggt dann bei jedem Exportversuch einen Fehler.
Ein eigenes Docker Compose für die lokalen Telemetrie-Services ist geplant (#5).

| Variable | Bedeutung |
| --- | --- |
| `OTEL_TRACES_EXPORTER`, `OTEL_METRICS_EXPORTER`, `OTEL_LOGS_EXPORTER` | `none` (kein Export) oder `otlp` |
| `OTEL_EXPORTER_OTLP_PROTOCOL` | `grpc` (Port 4317) oder `http/protobuf` (Port 4318, Standard des Agents) |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | OTLP-Ziel (SigNoz oder lokaler Collector), z. B. `http://localhost:4317` |
| `OTEL_EXPORTER_OTLP_HEADERS` | Zugangsdaten für gehostete Ziele, z. B. `signoz-ingestion-key=…` |
| `OTEL_TRACES_SAMPLER`, `OTEL_TRACES_SAMPLER_ARG` | Sampling – bei 20 Traces/s für gehostetes SigNoz empfohlen: `parentbased_traceidratio`, `0.1` |
| `LLM_BASE_URL`, `LLM_MODEL`, `LLM_API_KEY` | OpenAI-kompatibler Endpunkt für die Gegner-KI (LiteLLM) |

Langfuse nimmt OTLP **nur per HTTP** an (`https://<host>/api/public/otel`, Basic-Auth mit Public/Secret Key).
Geplant ist ein lokaler OpenTelemetry Collector als Verteiler: alles an SigNoz, nur die GenAI-Spans an Langfuse.

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
run-local.sh      Start ohne Docker (JVM-Prozesse), stop-local.sh beendet sie
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
- Die Spring-Services laufen per `run-local.sh` (Spiel, Leaderboard, Schwarm, OTLP-Export geprüft).
  Helm und der LLM-Pfad sind noch nicht gestartet worden.
- Helm-Chart nur mit eigenem Renderer geprüft, nicht mit `helm lint`.
