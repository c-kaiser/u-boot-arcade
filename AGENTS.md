# AGENTS.md – U-Boot Arcade

Anweisungen für Coding-Agenten. Alles, was für Menschen und Agenten gleichermaßen gilt
(Projektbeschreibung, Bauen, Starten, Konventionen), steht in der **[README.md](README.md)** – bitte zuerst lesen.
Die Architektur ist in **[arc-doc/](arc-doc/)** nach arc42 dokumentiert.

## Git und GitHub

- **Nie auf `main` committen oder pushen.** Jede Änderung auf einem eigenen Branch, Zusammenführen nur per Pull Request.
- **Keine GitHub Issues selbstständig anlegen.** Vorschläge für Issues erst dem Menschen vorlegen und nach Zustimmung anlegen.

## Leitplanken

- Vor dem Abschluss einer Änderung `./gradlew build` ausführen und das Ergebnis ehrlich berichten.
- Echte LLM-Modelle (LiteLLM im Workshop-Cluster) kosten Geld: keine Schleifen, Lasttests oder Chaos-Runaway gegen Cloud-Modelle
  laufen lassen, ohne vorher zu fragen. Für Tests den regelbasierten Gegner (`GAME_LLM_ENABLED=false`) nutzen.
- `opentelemetry-api` in `build.gradle.kts` muss zur Version des OTel-Java-Agents passen. Die Agent-Version steht im
  `Dockerfile` und in `run-local.sh` – alle drei nur gemeinsam ändern.
- Zum Ausprobieren `./run-local.sh` mit der Standard-`.env` (ohne Telemetrie, `GAME_LLM_ENABLED=false`), danach `./stop-local.sh`.
- `game-core` bleibt frei von Frameworks (kein Spring, keine OTel-Abhängigkeit).
- In Sandboxen mit Netzwerk-Firewall braucht der erste Build Zugriff auf `services.gradle.org`
  (leitet auf `release-assets.githubusercontent.com` weiter), `plugins.gradle.org` und Maven Central; `run-local.sh`
  lädt den OTel-Agent zusätzlich von `github.com` (Releases). Das Telemetrie-Compose (`deploy/telemetry`) braucht
  Docker Hub (`registry-1.docker.io`, `auth.docker.io`, `production.cloudfront.docker.com`) und bei jedem Start
  `github.com` (ein Init-Container lädt das ClickHouse-Hilfsprogramm `histogram-quantile` aus den SigNoz-Releases).

## arc42-Dokumentation

Dokumentationspfad für die arc42agentic-Agenten und -Skills (`.agents/`):

```
arc-doc/
```
