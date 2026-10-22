# Konventionen

| Konvention | Beschreibung | Verbindlichkeit |
|-----------|-------------|-----------------|
| Sprache | Deutsch für UI, Kommentare, Logs und Dokumentation; Code-Bezeichner Englisch | verbindlich |
| Branching | Nie direkt auf `main` committen oder pushen; jede Änderung auf eigenem Branch, Zusammenführen per Pull Request | verbindlich |
| Aufgaben | GitHub Issues; Agenten legen Issues nur nach Zustimmung an | verbindlich |
| Build vor Abschluss | `./gradlew build` vor Abschluss einer Änderung, Ergebnis ehrlich berichten | verbindlich |
| Modulgrenze | `game-core` bleibt frei von Frameworks (kein Spring, keine OTel-Abhängigkeit) | verbindlich |
| OTel-Versionen | `opentelemetry-api` und OTel-Java-Agent nur gemeinsam ändern | verbindlich |
| Konfiguration | Nur über Umgebungsvariablen (`.env`, Defaults in `application.yaml`); keine Adressen oder Secrets im Code | verbindlich |
| Telemetrie-Namen | Eigene Metriken mit Präfix (`game.*`, `sub.*`, `swarm.*`, `enemy_ai.*`, `leaderboard.*`); LLM-Telemetrie nach GenAI-Semantic-Conventions (`gen_ai.*`) <!-- ABGELEITET: Aus Code erkannt, bitte verifizieren --> | empfohlen |
| Packages | `io.github.ckaiser.uboot.<modul>` <!-- ABGELEITET: Aus Code erkannt, bitte verifizieren --> | empfohlen |
| Dokumentation | arc42 in `arc-doc/`, Markdown, Deutsch; Agenten und Skills in `.agents/` | verbindlich |
| Coding-Standard / Linting | <!-- TODO: Bitte ergänzen (kein Linter konfiguriert) --> | – |
| Test-Vorgaben | JUnit 5; Spiellogik in `game-core` getestet. Mindest-Coverage: <!-- TODO: Bitte ergänzen --> | – |
