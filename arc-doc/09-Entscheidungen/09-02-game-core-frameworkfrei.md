# ADR-02: Spiellogik in frameworkfreiem Modul `game-core`

**Status:** accepted (umgesetzt)

**Datum:** 2026-10-22 <!-- ABGELEITET: Datum des Commits, in dem die Entscheidung im Code sichtbar wird -->

## Kontext

Physik, regelbasierte Bot-KI und das LLM-Protokoll werden von mehreren Services gebraucht (physics, game-server,
enemy-ai) und sollen ohne Infrastruktur testbar sein.

## Entscheidung

`game-core` enthält Model, `PhysicsEngine`, `BotBrain` und `LlmProtocol` als reines Kotlin, ohne Spring und ohne
OTel-Abhängigkeit (Regel in AGENTS.md). Die Spring-Services binden es als `project(":game-core")` ein.

## Konsequenzen

### Positiv
- Schnelle Unit-Tests (16 grün), ohne Spring-Kontext.
- Dieselben Datenklassen dienen als JSON-Vertrag zwischen den Services.
- Derselbe `BotBrain` dient für Bots im Game-Server und als Fallback in enemy-ai.

### Negativ
- Keine Telemetrie aus dem Kern; Spans/Metriken entstehen nur in den Service-Adaptern.
- Ein Schema-Wechsel im Model betrifft alle Services gleichzeitig (gemeinsames Deployment nötig).
