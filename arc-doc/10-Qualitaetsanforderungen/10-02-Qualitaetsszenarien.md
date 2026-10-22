# Qualitätsszenarien

Kurzformat (Q42-Stil). Zahlen ohne Beleg sind als TODO markiert.

## QS-01: Chaos sichtbar in SigNoz

**Stimulus:** Trainer schaltet im Admin-Panel „Physik-Latenz 500 ms“ ein.
**Umgebung:** Normalbetrieb, Export an SigNoz, Metrik-Intervall 10 s (`.env.example`).
**Reaktion:** `game.tick.duration` steigt, `game.tick.overruns` zählt, Traces zeigen Span `chaos latency`, `chaos.injected` steigt.
**Metrik:** Sichtbar im SigNoz-Dashboard innerhalb von <!-- TODO: Bitte ergänzen (X s; Untergrenze ≈ Metrik-Exportintervall + Ingest-Latenz) -->.

## QS-02: LLM-Aufruf in Langfuse nachvollziehbar (geplant)

**Stimulus:** Gegner-KI trifft eine LLM-Entscheidung.
**Reaktion:** In Langfuse erscheint die Generation mit Prompt, Antwort, Tokens und Kosten, gruppiert pro Spielrunde/Gegner (`langfuse.session.id`).
**Metrik:** <!-- TODO: Bitte ergänzen --> – heute fehlen Prompt/Completion-Attribute und der Export an Langfuse.

## QS-03: Fachliche Werte pro Boot

**Stimulus:** Chaos „Kesselkühlung defekt“.
**Reaktion:** `sub.boiler.pressure` je `sub.name` steigt über 9,5 bar, `game.events{game.event.type=OVERHEAT}` steigt, Warn-Log „Kessel von … überhitzt“.
**Metrik:** Werte pro lebendem Boot in jedem Metrik-Export.

## QS-04: Tick-Dauer

**Stimulus:** 20 Ticks/s mit <!-- TODO: Bitte ergänzen (Anzahl Spieler, Bots, Schwarm-Clients) -->.
**Umgebung:** Normalbetrieb ohne Chaos, Workshop-VS-Code.
**Reaktion:** Tick inkl. Physik-Aufruf und Broadcast abgeschlossen.
**Metrik:** `game.tick.duration` p95 ≤ 50 ms (= `GAME_TICK_MILLIS`), `game.tick.overruns` ≈ 0. <!-- TODO: Bitte ergänzen – Zielwert bestätigen, noch nie gemessen -->

## QS-05: Langsamer Client

**Stimulus:** Ein Client nimmt Frames nur verzögert ab.
**Reaktion:** Für ihn werden alte Frames verworfen; andere Clients und der Tick bleiben unbeeinflusst.
**Metrik:** `game.websocket.frames_dropped{client.kind}` steigt, `game.tick.duration` unverändert.

## QS-06: LLM-Kosten begrenzt

**Stimulus:** LLM-Gegner gegen ein Cloud-Modell eingeschaltet, ggf. Chaos „KI-Endlosschleife“.
**Reaktion:** Kosten bleiben unter dem Budget; Überschreitung wird vom Gateway abgelehnt, Gegner fallen auf `BotBrain` zurück.
**Metrik:** `enemy_ai.llm.cost` pro Workshop ≤ <!-- TODO: Bitte ergänzen (USD) -->. Heute nur gemessen, nicht begrenzt.

## QS-07: Ausfall eines Nachbarservices

**Stimulus:** physics, enemy-ai oder leaderboard fällt aus bzw. antwortet mit Fehlern.
**Reaktion:** Game-Server tickt weiter; Welt friert bei Physik-Ausfall ein, Gegner behalten ihre Steuerung, Leaderboard-Einträge werden verworfen.
**Metrik:** Kein Absturz des Game-Servers; Fehler sichtbar in `game.physics.failures`, `game.enemy.decisions{outcome=unreachable}`, `game.leaderboard.dropped`.

## QS-08: Start ohne Docker

**Stimulus:** Entwickler/in oder Teilnehmende/r startet lokal, in GitHub Codespaces oder im Browser-VS-Code mit vorhandenem JDK ≥ 21, ohne Docker.
**Reaktion:** Ein Skript baut die Services, lädt den OTel-Agent, startet 5 Services mit Logs unter `logs/`.
**Metrik:** Lauffähig in < <!-- TODO: Bitte ergänzen (N Minuten, inkl. erstem Gradle-/JDK-Download) -->. Das Skript ist noch nicht umgesetzt.

## QS-09: Telemetrie-Ziel wechseln

**Stimulus:** Umstieg von lokalem SigNoz auf gehostetes SigNoz.
**Reaktion:** Nur `.env` (`OTEL_EXPORTER_OTLP_ENDPOINT`, `OTEL_EXPORTER_OTLP_HEADERS`) ändern, Neustart.
**Metrik:** Keine Code- oder Build-Änderung.

## QS-10: Modellwechsel

**Stimulus:** Wechsel von Ollama auf Anthropic oder Heribert.
**Reaktion:** Eintrag in `deploy/litellm-config.yaml` bzw. `LLM_MODEL`/`LLM_BASE_URL` ändern.
**Metrik:** Keine Codeänderung in enemy-ai.

## QS-11: Testbarkeit der Spiellogik

**Stimulus:** Änderung an Physik oder Bot-KI.
**Reaktion:** `./gradlew :game-core:test` prüft ohne Spring, Netz oder Datenbank.
**Metrik:** Tests laufen lokal ohne Infrastruktur (heute 16 Tests). Laufzeit: <!-- TODO: Bitte ergänzen -->

## QS-12: Betrieb ohne Telemetrie-Backend (geplant)

**Stimulus:** Die Services werden gestartet, ohne dass SigNoz, ein Collector oder Langfuse läuft bzw. erreichbar ist.
**Umgebung:** Start ohne Docker, lokal oder in GitHub Codespaces; ggf. mit einer eigenen Konfiguration, die den Export abschaltet.
**Reaktion:** Alle Services starten, das Spiel ist spielbar; fehlgeschlagene Exporte führen höchstens zu Warn-Logs.
**Metrik:** Kein Absturz, kein Hänger beim Start, Tick-Dauer wie im Normalbetrieb (QS-04). <!-- TODO: Bitte ergänzen – noch nicht geprüft, welche Konfiguration nötig ist -->
