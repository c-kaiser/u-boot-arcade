# AGENTS.md – U-Boot Arcade

Anweisungen für Coding-Agenten. Alles, was für Menschen und Agenten gleichermaßen gilt
(Projektbeschreibung, Bauen, Starten, Konventionen), steht in der **[README.md](README.md)** – bitte zuerst lesen.
Die Architektur ist in **[arc-doc/](arc-doc/)** nach arc42 dokumentiert.

## Git und GitHub

- **Nie auf `main` committen oder pushen.** Jede Änderung auf einem eigenen Branch, Zusammenführen nur per Pull Request.
- **Keine GitHub Issues selbstständig anlegen.** Vorschläge für Issues erst dem Menschen vorlegen und nach Zustimmung anlegen.

## Leitplanken

- Vor dem Abschluss einer Änderung `./gradlew build` ausführen und das Ergebnis ehrlich berichten.
- Echte LLM-Modelle (Anthropic, Heribert) kosten Geld: keine Schleifen, Lasttests oder Chaos-Runaway gegen Cloud-Modelle
  laufen lassen, ohne vorher zu fragen. Für Tests den regelbasierten Gegner (`GAME_LLM_ENABLED=false`) nutzen.
- `opentelemetry-api` in `build.gradle.kts` muss zur Version des OTel-Java-Agents passen – nur gemeinsam ändern.
- `game-core` bleibt frei von Frameworks (kein Spring, keine OTel-Abhängigkeit).

## arc42-Dokumentation

Dokumentationspfad für die arc42agentic-Agenten und -Skills (`.agents/`):

```
arc-doc/
```
