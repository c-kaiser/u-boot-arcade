# Lösungsstrategie

## Technologieentscheidungen

| Technologie | Entscheidung | Begründung |
|------------|-------------|-----------|
| Programmiersprache | Kotlin 2.2.21, Bytecode Java 21 (`jvmTarget 21`, siehe ADR-08) | Kompakter Code, Data Classes für das Spielmodell; JDK 21 für virtuelle Threads. <!-- ABGELEITET: Aus Code erkannt, bitte verifizieren --> |
| Framework | Spring Boot 3.5.16 (Web, WebSocket, RestClient, JDBC) | Vom OTel-Java-Agent vollständig instrumentiert, bekannter Stack. <!-- ABGELEITET --> |
| Telemetrie | OTel-Java-Agent 2.32.0 (Zero-Code) + `opentelemetry-api` 1.66.0 über `GlobalOpenTelemetry` | HTTP, JDBC, Logs, JVM ohne Codeänderung; eigener Code nur für fachliche Spans/Metriken und GenAI-Telemetrie |
| LLM-Anbindung | OpenAI-kompatible API, LiteLLM als Gateway (Modellalias `enemy-brain`) | Modell wechseln ohne Codeänderung (Anthropic, Heribert; kein lokales Modell, ADR-14); LiteLLM liefert eigene Spans und Kosten |
| Datenbank | H2 als eingebettete Datei-DB über JDBC, in allen Verteilungsvarianten (ADR-07) | JDBC-Spans durch den Agent; keine DB-Infrastruktur, weil im Workshop kein Docker; nur ein DB-Dialekt |
| Build | Gradle Wrapper 9.6.0, Multi-Projekt | Läuft in allen Zielumgebungen mit JDK ≥ 21, ohne weitere Installation |
| Betrieb | JVM-Prozesse ohne Docker (`run-local.sh`, lokal und in Codespaces) oder Helm-Chart (Kubernetes), kein Compose für die Anwendung (ADR-11); Telemetrie-Ziel nur per Konfiguration (ADR-10) | Workshop-Umgebung ohne Docker; lokal SigNoz per Telemetrie-Compose (`deploy/telemetry`, ADR-13), Langfuse geplant (#11) |

## Top-Level-Zerlegung

**Gewähltes Architekturmuster:** kleine verteilte Services um einen zentralen, zustandsbehafteten Game-Server
(Tick-Loop, 20 Ticks/s) plus frameworkfreie Bibliothek `game-core` mit der gesamten Spiellogik.

```
Browser ──WS──► game-server ──HTTP──► physics (zustandslos, skalierbar)
player-swarm ─WS─►  │ 20 Ticks/s
                    ├──► enemy-ai ──► LiteLLM ──► Modell   (Fallback: regelbasierter BotBrain)
                    └──► leaderboard ──JDBC──► H2 (eingebettet, Datei)
```

**Begründung:** <!-- ABGELEITET: Aus Code erkannt, bitte verifizieren -->
- Mehrere Services erzeugen **verteilte Traces** und typische Fehlerbilder (Latenz, Timeouts, Rückstau), die ein
  Monolith nicht zeigen würde. Jeder Service ist ein eigener Störpunkt.
- Spiellogik in `game-core` bleibt testbar und unabhängig von Spring/OTel; die Services sind dünne Hüllen.
- Der **Game-Server bleibt eine Replica** (Spielstand im Speicher); die **Physik ist zustandslos** und skaliert horizontal.
- Alternative Monolith: einfacher zu betreiben, aber weniger aussagekräftige Telemetrie. <!-- TODO: Bitte ergänzen (weitere Alternativen?) -->

Details: [Kapitel 5](../05-Bausteinsicht/), Entscheidungen in [Kapitel 9](../09-Entscheidungen/).

## Ansätze zur Erreichung der Qualitätsziele

| Qualitätsziel | Ansatz | Status |
|--------------|--------|--------|
| Beobachtbarkeit | Agent für Standard-Telemetrie; eigene Metriken für Spiel (`game.*`), Boote (`sub.*`), Clients, Leaderboard; Spielereignisse als strukturierte Logs mit Trace-ID; GenAI-Span `chat <model>` nach GenAI-Semconv; Trace-Kontext bis LiteLLM durchgereicht | umgesetzt |
| Beobachtbarkeit (Langfuse) | Lokaler OTel Collector als Fan-out (alles an SigNoz, nur GenAI-Spans an Langfuse); Prompt/Completion, `langfuse.session.id`, `gen_ai.usage.cost` | **geplant** |
| Störbarkeit | `Chaos`-Objekt im Game-Server, über Admin-Panel änderbar, mit jedem Aufruf an die Services durchgereicht; Werte begrenzt (z. B. Physik-Latenz ≤ 1500 ms unter 2-s-Timeout) | umgesetzt (global) |
| Störbarkeit pro U-Boot | Störung pro Boot auf einer Skala in der UI | **geplant**, Umsetzung offen |
| Einfache Inbetriebnahme | Gradle Wrapper, nur JDK ≥ 21 nötig; Konfiguration nur über Umgebungsvariablen; Dev Container für Codespaces | umgesetzt |
| Einfache Inbetriebnahme ohne Docker | `run-local.sh`/`stop-local.sh` mit OTel-Agent, `.env`, Logs nach `logs/`; Standard „ohne Telemetrie“ (ADR-10) | umgesetzt; LiteLLM per `pip` **geplant** |
| Kostenkontrolle LLM | Regelbasierter Fallback, LLM per Schalter (Default aus), `max_tokens` 60, Kostenmetrik `enemy_ai.llm.cost`, LiteLLM als zentrale Stelle | umgesetzt |
| Kostenkontrolle LLM (Bremse) | Runaway-Chaos bei Cloud-Modellen deckeln, LiteLLM `max_budget`; Entscheidungsintervall bleibt bei ≈ 1 s | **geplant** |
| Flüssiges Spiel | Fester Tick (50 ms) im eigenen Thread; Gegner-KI und Leaderboard asynchron; Broadcast pro Client über virtuelle Threads, alte Frames werden verworfen (`game.websocket.frames_dropped`); Timeouts an allen HTTP-Clients | umgesetzt |
| Datenvolumen | Sampling `parentbased_traceidratio` per Umgebungsvariable | konfigurierbar, nicht voreingestellt |

Details: [Kapitel 8](../08-Konzepte/).

## Organisatorische Entscheidungen

- Änderungen nur auf Branches mit Pull Request, Aufgaben als GitHub Issues.
- Coding-Agenten arbeiten mit Leitplanken aus [AGENTS.md](../../AGENTS.md) (u. a. keine teuren LLM-Läufe ohne Rückfrage).
- arc42-Doku wird mit den Agenten und Skills aus `.agents/` geschrieben und geprüft.
