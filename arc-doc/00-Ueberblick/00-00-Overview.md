# Überblick

**U-Boot Arcade** ist ein kleines Multiplayer-Arcade-Spiel mit U-Booten. Es wird als „eigene App“ in einen
INNOQ-Workshop zu Observability (OpenTelemetry, SigNoz, Langfuse) mitgebracht. Das Spiel erzeugt technische
Telemetrie (Latenzen, Fehler, JVM, DB) und fachliche Telemetrie (Tiefe, Dampfdruck, Treffer) und lässt sich über
Chaos-Schalter gezielt stören, damit man die Folgen in SigNoz und Langfuse wiederfinden kann.

Technisch besteht es aus fünf Kotlin/Spring-Boot-Services und einer frameworkfreien Spiellogik-Bibliothek
(`game-core`). Die Telemetrie kommt größtenteils vom OpenTelemetry-Java-Agent, dazu eigene Spans und Metriken über die OTel-API.

Diese Dokumentation folgt [arc42](https://arc42.org). Sie unterscheidet durchgehend zwischen **umgesetzt**
(im Code vorhanden) und **geplant** (offene Aufgaben).

Marker im Text:
- `<!-- TODO: Bitte ergänzen -->`: Information fehlt und muss nachgetragen werden
- `<!-- ABGELEITET: Aus Code erkannt, bitte verifizieren -->`: aus Code und Konfiguration abgeleitet

## Inhaltsverzeichnis

| Nr. | Kapitel | Inhalt |
|-----|---------|--------|
| 1 | [Einführung und Ziele](../01-Einfuehrung-und-Ziele/) | Aufgabenstellung, Qualitätsziele, Stakeholder |
| 2 | [Randbedingungen](../02-Randbedingungen/) | Technische und organisatorische Vorgaben, Konventionen |
| 3 | [Kontextabgrenzung](../03-Kontextabgrenzung/) | Fachlicher und technischer Kontext |
| 4 | [Lösungsstrategie](../04-Loesungsstrategie/) | Technologien, Zerlegung, Ansätze für die Qualitätsziele |
| 5 | [Bausteinsicht](../05-Bausteinsicht/) | Services und Module |
| 6 | [Laufzeitsicht](../06-Laufzeitsicht/) | Wichtige Abläufe (Tick, Gegner-KI, Leaderboard, Chaos) |
| 7 | [Verteilungssicht](../07-Verteilungssicht/) | Betrieb lokal, Docker Compose, Kubernetes, Workshop |
| 8 | [Querschnittliche Konzepte](../08-Konzepte/) | Telemetrie, Chaos, Konfiguration u. a. |
| 9 | [Architekturentscheidungen](../09-Entscheidungen/) | Wichtige Entscheidungen |
| 10 | [Qualitätsanforderungen](../10-Qualitaetsanforderungen/) | Qualitätsbaum und -szenarien |
| 11 | [Risiken und technische Schulden](../11-Risiken/) | Bekannte Risiken |
| 12 | [Glossar](../12-Glossar/) | Begriffe und Abkürzungen |

## Weitere Quellen

- [README.md](../../README.md): Bauen, Starten, Telemetrie, Chaos-Schalter
- [AGENTS.md](../../AGENTS.md): Leitplanken für Coding-Agenten
