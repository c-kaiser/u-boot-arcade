# Glossar

<!-- ABGELEITET: Aus Code erkannt, bitte verifizieren -->

## Domänenbegriffe (Spiel)

| Begriff | Begriff im Code | Definition |
|---------|----------------|-----------|
| U-Boot | `Sub` | Spielfigur mit Position (x, Tiefe), Geschwindigkeit, Kessel, Rumpf, Punkten. Unveränderlich, die Physik liefert pro Tick neue Instanzen. |
| Bootsart | `SubKind` | Wer ein U-Boot steuert: `PLAYER` (Mensch im Browser oder Schwarm-Client), `BOT` (regelbasiert), `ENEMY` (Gegner-KI). |
| Spieler*in | `PLAYER` | U-Boot, das ein Mensch (oder `player-swarm`) per WebSocket steuert. |
| Bot | `BOT` | Vom Game-Server regelbasiert gesteuertes U-Boot (`BotBrain`). |
| Gegner | `ENEMY` | U-Boot, das die Gegner-KI steuert (LLM oder regelbasierter Fallback). Namen z. B. Kraken, Leviathan, Nautilus. |
| Welt | `World` | Spielzustand eines Ticks: Tick-Nummer, alle U-Boote, alle Torpedos. |
| Tick | `tick` | Ein Simulationsschritt des Game-Servers, 20 pro Sekunde (50 ms). |
| Steuerbefehl | `Controls` | Eingabe für einen Tick: Fahrt (`throttle` −1..1), Tauchen (`dive` −1..1), Torpedo feuern. |
| Torpedo | `Torpedo` | Geschoss mit Besitzer, Position, Geschwindigkeit und Lebensdauer; ein Treffer kostet 40 Rumpfpunkte. |
| Dampfdruck / Kessel | `boilerPressure`, `boilerTemp` | Antrieb des U-Boots. Nennwert 5 bar; über 9,5 bar Überhitzung (`OVERHEAT`), über 11,5 bar bläst das Sicherheitsventil ab (`VALVE_VENT`). |
| Rumpf | `hull` | Rumpfintegrität 0–100; bei 0 ist das Boot zerstört. |
| Zerstörungstiefe | `CRUSH_DEPTH` | 420 m; darunter nimmt der Rumpf laufend Schaden. Maximale Tiefe 600 m. |
| Runde | `ScoreEntry` | Ein Leben eines U-Boots bis zur Zerstörung; ihr Ergebnis (Punkte, Abschüsse, Ursache) geht ans Leaderboard. |
| Respawn | `RESPAWN` | Wiedereintritt eines zerstörten Boots nach 3 s. |
| Spielereignis | `GameEvent`, `EventType` | Fachliches Ereignis eines Ticks: `TORPEDO_FIRED`, `HIT`, `KILL`, `DESTROYED`, `RESPAWN`, `OVERHEAT`, `VALVE_VENT`, `CRUSH_DEPTH`, `SURFACED`. Als Log und Metrik `game.events` sichtbar. |
| Leaderboard / Bestenliste | `leaderboard` | Gespeicherte Rundenergebnisse und Top-Liste. |

## Begriffe aus Workshop und Betrieb

| Begriff | Definition |
|---------|-----------|
| Chaos / Chaos-Schalter | Gezielt auslösbare Störung (`Chaos`), heute global im Admin-Panel: Physik-Latenz, Physik-Fehlerrate, Leaderboard langsam (verzögert den Insert-Request, nicht die DB), KI-Latenz, KI-Endlosschleife, Memory Leak, Kesselkühlung defekt, Leck im Rumpf, Meeresströmung. Zielbild (**geplant**): Störung pro U-Boot auf einer Skala. |
| KI-Endlosschleife (Runaway) | Chaos-Schalter `aiRunaway`: Gegner-KI fragt das LLM bis zu 8× pro Entscheidung mit wachsendem Kontext; treibt Tokens und Kosten. |
| Admin-Panel | `admin.html` im Game-Server: Bots, Gegner, LLM an/aus, Chaos-Schalter. |
| Fallback | Regelbasierte Gegner-Entscheidung, wenn LLM aus ist oder fehlschlägt (`source=fallback`, `outcome` z. B. `timeout`, `parse_error`). |
| Schwarm | `player-swarm`: Lastgenerator mit echten WebSocket-Clients. |
| Heribert | Vom Veranstalter bereitgestellter LLM-Zugang; Art des Endpunkts unklar, vermutlich OpenAI-kompatibel. <!-- TODO: Bitte ergänzen --> |
| Jev | „System One“-Modell von TypeSafe mit eigener, nicht OpenAI-kompatibler API; optionale dritte Gegner-KI-Variante (**geplant**, nur mit Zugang). |
| `enemy-brain` | Modellalias in LiteLLM, den die Gegner-KI aufruft; welches echte Modell dahinter steckt, entscheidet nur die LiteLLM-Konfiguration. |
| Workshop-Umgebung | Codespace bzw. Browser-VS-Code pro Person (Anwendung ohne Docker per `run-local.sh`), ggf. ein Kubernetes-Cluster; dazu gehostetes SigNoz, OTel Demo und Langfuse. |
| Telemetrie-Betriebsart | Wohin die Telemetrie geht, gewählt nur in `.env`: *ohne Telemetrie* (Standard, `OTEL_*_EXPORTER=none`), *lokal* (SigNoz/Collector auf dem eigenen Rechner) oder *bereitgestellt* (gehostete Ziele mit Zugangsdaten), siehe ADR-10. Nicht zu verwechseln mit der Verteilungsvariante. |
| Verteilungsvariante | Wie die Services betrieben werden: ohne Docker (`run-local.sh`, lokal oder in Codespaces) oder Kubernetes (Helm), siehe Kapitel 7. Unabhängig von der Telemetrie-Betriebsart. |
| o11y-playground | Begleitprojekt, aus dem SigNoz lokal in Docker gestartet werden kann. <!-- TODO: Bitte ergänzen (Link) --> |

## Technische Begriffe

| Begriff | Definition | Kontext |
|---------|-----------|---------|
| OpenTelemetry (OTel) | Herstellerneutraler Standard für Traces, Metriken und Logs | Agent + API in allen Services |
| OTel-Java-Agent | Zur Laufzeit per `-javaagent` geladene Zero-Code-Instrumentierung (HTTP, JDBC, Logs, JVM) | Version 2.32.0 |
| `run-local.sh` / `stop-local.sh` | Startet bzw. beendet die Services ohne Docker als JVM-Prozesse mit OTel-Agent (Logs `logs/`, PIDs `.run/`) | Verteilungssicht 7.2 |
| OTLP | OpenTelemetry Protocol (gRPC oder HTTP) zum Export der Telemetrie | Ziel per `OTEL_EXPORTER_OTLP_ENDPOINT` |
| OTel Collector | Eigenständiger Prozess, der Telemetrie empfängt, filtert und weiterleitet | **geplant** als Fan-out SigNoz/Langfuse |
| SigNoz | Observability-Plattform für Traces, Metriken und Logs | Lokal in Docker oder gehostet |
| Langfuse | LLM-Observability-Plattform (Prompts, Completions, Kosten, Sessions); OTLP nur per HTTP | **geplant** |
| LiteLLM | Proxy mit OpenAI-kompatibler API vor verschiedenen LLM-Anbietern, mit Kosten-Tracking und Budget | Gateway der Gegner-KI |
| Ollama | Laufzeit für lokale Modelle (hier `qwen2.5:3b`) | nativ neben LiteLLM, Alias `enemy-brain` |
| GenAI-Semantic-Conventions | OTel-Namenskonventionen für LLM-Telemetrie (`gen_ai.*`) | Span `chat <model>`, `gen_ai.client.token.usage` |
| Sampling | Nur ein Teil der Traces wird exportiert (`parentbased_traceidratio`) | Gegen 20 Traces/s bei gehostetem SigNoz |
| Overrun | Tick dauert länger als 50 ms (`game.tick.overruns`) | Zeichen für Überlast/Chaos |
| Frame verworfen | Ein Zustandsupdate, das ein langsamer Client nicht abgenommen hat, wird zugunsten des neuesten verworfen (`game.websocket.frames_dropped`) | Broadcast im Game-Server |
| Virtuelle Threads | Leichtgewichtige Threads ab JDK 21 | Broadcast pro Client |

## Abkürzungen

| Abkürzung | Bedeutung |
|-----------|-----------|
| o11y | Observability (11 Buchstaben zwischen o und y) |
| LLM | Large Language Model |
| RTT | Round-Trip-Time (`game.client.rtt`) |
| WS | WebSocket |
| H2 | Eingebettete Java-Datenbank; das Leaderboard nutzt sie als Datei-DB in allen Verteilungsvarianten (ADR-07) |
| ADR | Architecture Decision Record |
