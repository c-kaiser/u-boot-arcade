# U-Boot Arcade

Ein kleines Multiplayer-Arcade-Spiel mit U-Booten. Gebaut als Spielwiese für den Observability-Workshop:
Es erzeugt technische Telemetrie (Latenzen, Fehler, JVM, DB) und fachliche Telemetrie (Tiefe, Dampfdruck, Treffer),
und es lässt sich auf Knopfdruck kaputtmachen.

```
 Browser ──WebSocket──► game-server ──HTTP──► physics (x2, zustandslos)
 player-swarm ──WS──►   │  20 Ticks/s
                        ├──HTTP──► enemy-ai ──HTTP──► LiteLLM ──► Ollama / Cloud-LLM
                        └──HTTP──► leaderboard ──JDBC──► Postgres
                 alle ──OTLP──► SigNoz (Traces, Metriken, Logs)
```

Alle Services sind Kotlin/Spring Boot. Den Großteil der Telemetrie liefert der OpenTelemetry-Java-Agent ohne Codeänderung
(HTTP-Server/-Clients, JDBC, Logs, JVM-Metriken). Dazu kommen eigene Spans und Metriken über die OTel-API.

## Schnellstart (Laptop)

Voraussetzungen: Docker mit Compose v2. Optional läuft SigNoz lokal und nimmt OTLP auf Port 4317 an,
zum Beispiel aus dem `o11y-playground`.

```bash
cp .env.example .env
docker compose up --build                  # Spiel, 3 Bots, 2 regelbasierte Gegner
docker compose --profile swarm up --build  # + 10 Schwarm-Spieler (WebSocket-Last)
docker compose --profile llm up --build    # + LiteLLM und Ollama (qwen2.5:3b) für die Gegner-KI
```

- Spiel: http://localhost:8088 (← → Fahrt, ↑ ↓ Tauchen, Leertaste Torpedo)
- Admin & Chaos: http://localhost:8088/admin.html (dort auch „LLM für Gegner verwenden“ einschalten)
- SigNoz: http://localhost:8080 (Services erscheinen unter `service.namespace=u-boot-arcade`)

Der erste Build dauert einige Minuten (Gradle lädt die Abhängigkeiten). Tests der Spiellogik:
`gradle test` (Gradle 8.14+, JDK 21) oder ohne lokales Gradle
`docker run --rm -v "$PWD":/src -w /src gradle:8.14-jdk21 gradle test`.

## Im Workshop-Cluster (Helm)

```bash
deploy/build-images.sh ghcr.io/<org> 1.0.0     # baut und pusht 5 Images
helm upgrade --install u-boot deploy/helm/u-boot-arcade -n u-boot --create-namespace \
  --set image.registry=ghcr.io/<org> \
  --set otel.endpoint=http://<signoz-collector-service>.<namespace>.svc.cluster.local:4317
kubectl -n u-boot port-forward svc/u-boot-game-server 8088:80
```

Mehr Last: `kubectl -n u-boot scale deploy/u-boot-player-swarm --replicas=5`.
Physik skalieren: `--set physics.replicas=4`. Der Game-Server bleibt bei einer Replica, weil der Spielstand im Speicher liegt.
Für ein LLM im Cluster `llm.baseUrl` auf euren LiteLLM-Service setzen und `game.llmEnabled=true`.

## Was man beobachten kann

### Traces
- **`game.tick`** (20 pro Sekunde): Physik-Aufruf, `game.broadcast`, asynchron die Gegner-Entscheidungen
  und Leaderboard-Einträge (`INSERT` und eventuell `pg_sleep` als DB-Span)
- **`chat enemy-brain`** in `enemy-ai`: GenAI-Span mit `gen_ai.usage.input_tokens`/`output_tokens` und Kosten.
  Darunter der HTTP-Span zu LiteLLM und dessen eigene Spans, die Trace-ID wird durchgereicht.
- Bei 20 Traces/s lohnt sich Sampling: `OTEL_TRACES_SAMPLER=parentbased_traceidratio`, `OTEL_TRACES_SAMPLER_ARG=0.1`

### Eigene Metriken (Auswahl)

| Metrik | Bedeutung |
| --- | --- |
| `game.tick.duration`, `game.tick.overruns` | Schafft der Server seine 50 ms pro Tick? |
| `game.client.rtt` (`client.kind`) | WebSocket-Round-Trip aus Browser und Schwarm |
| `swarm.state.interval` | Abstand zwischen Updates beim Client (Ruckeln) |
| `game.websocket.frames_dropped` | Updates, die ein langsamer Client nicht abgenommen hat |
| `game.physics.failures` | Physik-Aufrufe, die fehlgeschlagen sind (Welt friert ein) |
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

## Chaos-Schalter (admin.html)

| Schalter | Wirkung | Woran man es erkennt |
| --- | --- | --- |
| Physik-Latenz | Jeder Step wird verzögert | `game.tick.duration` steigt, Overruns, Spiel ruckelt |
| Physik-Fehlerrate | Steps scheitern mit 503 | Fehler-Spans, `game.physics.failures`, Welt friert ein |
| Leaderboard-DB langsam | `pg_sleep` vor jedem Insert | Langer DB-Span, Warteschlange läuft voll |
| Gegner-KI-Latenz | Gegner reagieren träge | `game.enemy.decision.latency` |
| Gegner-KI-Endlosschleife | 8 LLM-Aufrufe pro Entscheidung, Kontext wächst | Tokens und Kosten explodieren, lange Traces |
| Memory Leak | Heap wächst um ~1,3 MB/s | JVM-Heap-Metriken, Garbage Collection, irgendwann OOM-Kill |
| Kesselkühlung defekt | Dampfdruck über 9,5 bar | `sub.boiler.pressure`, OVERHEAT-Events, Alerts |
| Leck im Rumpf | Boote sinken | `sub.depth` steigt, `sub.hull` fällt |
| Meeresströmung | Boote treiben ab | `sub.speed` |

## Ideen für Teams

- Dashboard „Brücke“: Tiefe, Druck und Rumpf je Boot, dazu Ereignisse als Zeitreihe
- Alert auf `sub.boiler.pressure > 9.5` und auf p95 von `game.tick.duration > 50 ms`
- SLO: 99 % der Ticks unter 50 ms. Welcher Chaos-Schalter reißt es?
- Kosten pro Gegner und Stunde aus `enemy_ai.llm.cost`. Lokales Modell und Cloud-Modell vergleichen
- Langfuse an LiteLLM hängen und die Qualität der Gegner-Entscheidungen bewerten
- kAgent fragen: „Warum ruckelt das Spiel?“, während Physik-Latenz aktiv ist

## Projektstruktur

```
game-core/        reine Spiellogik (Physik, Bot-KI, LLM-Prompt/Parsing) + Tests, ohne Frameworks
physics-service/  POST /api/step – zustandslose Physik
game-server/      Tick-Loop, WebSocket /ws, Admin-API, Frontend (src/main/resources/static)
enemy-ai/         POST /api/decide – LLM über LiteLLM, sonst regelbasiert
leaderboard/      POST /api/scores, GET /api/scores/top – Postgres über JDBC
player-swarm/     Lastgenerator mit echten WebSocket-Clients
deploy/           LiteLLM-Konfiguration, Helm-Chart, Build-Skript
```
