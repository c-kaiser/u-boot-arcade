# Aufgabenstellung

## Kurzbeschreibung

U-Boot Arcade ist ein Multiplayer-Arcade-Spiel im Browser: Spieler, Bots und KI-Gegner steuern U-Boote durch ein
2D-Meer und beschießen sich mit Torpedos. Das Spiel ist die „eigene App“ für einen Observability-Workshop
(OpenTelemetry, SigNoz, Langfuse). Es soll technische und fachliche Telemetrie erzeugen und sich gezielt stören lassen,
damit Teilnehmende die Folgen in SigNoz und Langfuse wiederfinden.

## Business-Ziele

- **Lernobjekt für Observability:** realistische Traces, Metriken und Logs über mehrere Services, inklusive
  DB- und LLM-Aufrufen.
- **Fachliche Telemetrie zeigen:** Domänenwerte (Tiefe, Dampfdruck, Rumpf, Treffer) als Metriken und Events,
  nicht nur technische Signale.
- **Störungen erlebbar machen:** Chaos auslösen und die Ursache anhand der Telemetrie finden.
- **GenAI-Observability:** LLM-Aufrufe der Gegner-KI mit Tokens und Kosten nach GenAI-Semantic-Conventions sichtbar machen (SigNoz, Langfuse).
- **Rechtzeitig fertig:** Das Spiel soll vor dem Workshop lauffähig sein. <!-- TODO: Bitte ergänzen (Workshop-Termin) -->

## Wesentliche funktionale Anforderungen

| ID | Anforderung | Beschreibung | Status |
|----|------------|--------------|--------|
| F1 | Multiplayer-Spiel | Spieler steuern ein U-Boot im Browser (← → Fahrt, ↑ ↓ Tauchen, Leertaste Torpedo), Echtzeit-Updates per WebSocket, 20 Ticks/s | umgesetzt |
| F2 | Bots und Gegner | Regelbasierte Bots (`BOT`) und Gegner (`ENEMY`); Gegner optional per LLM gesteuert, mit regelbasiertem Fallback | umgesetzt |
| F3 | Physik | Bewegung, Kessel (Dampfdruck, Temperatur), Rumpf, Tauchtiefe, Torpedotreffer in einem zustandslosen Physik-Service | umgesetzt |
| F4 | Leaderboard | Ergebnis jeder Runde (ein Leben) speichern, Bestenliste anzeigen | umgesetzt (eingebettete H2-Datei-DB) |
| F5 | Admin-Panel | Anzahl Bots/Gegner, LLM an/aus und Chaos-Schalter zur Laufzeit ändern (`admin.html`) | umgesetzt |
| F6 | Chaos-Schalter | Globale Störungen: Physik-Latenz/-Fehlerrate, langsame Leaderboard-DB, KI-Latenz, KI-Endlosschleife, Memory Leak, Kesselkühlung, Leck, Strömung | umgesetzt |
| F7 | Chaos pro U-Boot | In der UI pro U-Boot eine Störung auf einer Skala einstellen, die gezieltes Chaos (z. B. Latenz) auslöst | geplant, Umsetzung offen |
| F8 | Telemetrie | Traces, Metriken, Logs per OTLP; eigene fachliche Metriken und GenAI-Spans | umgesetzt |
| F9 | Lastgenerator | `player-swarm` mit echten WebSocket-Clients für Last und Client-Metriken | umgesetzt |
| F10 | Export an SigNoz und Langfuse | Lokaler OTel Collector als Verteiler: alles an SigNoz, nur GenAI-Spans an Langfuse | geplant |
| F11 | Start ohne Docker | Die Anwendung startet lokal und in GitHub Codespaces als JVM-Prozesse ohne Docker (`run-local.sh`/`stop-local.sh`, Konfiguration über `.env`) | umgesetzt (#3), Codespaces noch nicht erprobt |
| F12 | Betrieb ohne Telemetrie-Backend | Die Anwendung läuft auch, wenn kein Telemetrie-Ziel (SigNoz, Collector, Langfuse) gestartet oder erreichbar ist – Standard ist die Telemetrie-Betriebsart „ohne Telemetrie“ (`OTEL_*_EXPORTER=none`, ADR-10) | umgesetzt (#3), Codespaces noch nicht erprobt |
| F13 | Darstellungsvariante | Spiel und Admin-Panel wahlweise als U-Boot (Standard) oder als „Slop Arcade“ (KI-Agenten werfen mit Slop, Menschen mit Verstand); pro Browser umschaltbar, ändert nur die Anzeige (ADR-12) | umgesetzt, nur per Smoke-Test ohne Browser geprüft |

<!-- ABGELEITET: Aus Code erkannt, bitte verifizieren -->
<!-- TODO: Anforderungen nach Erreichen des Initialstands erneut prüfen. Initialstand = grundsätzliches Review erfolgt und ein erster Durchstich funktional und abgenommen -->

## Status der Umsetzung

- `game-core` kompiliert, Tests grün. Frontend gegen Mock-Server getestet.
- Alle Module kompilieren (`./gradlew build`). Die Spring-Services laufen per `run-local.sh` (Spiel, Leaderboard, Schwarm,
  OTLP-Export geprüft); Helm und der LLM-Pfad sind noch nicht gestartet (R1).
- Weitere geplante Punkte: Kostenbremse für echte LLMs, Langfuse-Attribute (Prompt/Completion, Session-ID),
  Anthropic- und Heribert-Anbindung in LiteLLM, optional Jev als dritte Gegner-KI-Variante.
  Details in [Kapitel 11](../11-Risiken/) bzw. als GitHub Issues.

## Verweise

- [README.md](../../README.md)
- GitHub Issues des Repositorys <!-- TODO: Bitte ergänzen (Link) -->
- Begleitdokumente im claude.ai-Projekt „observability-workshop-2026“ <!-- TODO: Bitte ergänzen (Link/Ablage) -->
