# ADR-01: OTel-Java-Agent zur Laufzeit, im Code nur die OTel-API

**Status:** accepted (umgesetzt)

**Datum:** 2026-10-22 <!-- ABGELEITET: Datum des Commits, in dem die Entscheidung im Code sichtbar wird -->

## Kontext

Das Spiel soll im Workshop möglichst viel Telemetrie zeigen (HTTP, JDBC, Logs, JVM) und zugleich eigene fachliche
Spans/Metriken liefern (README, Dockerfile-Kommentar „Zero-Code-Instrumentierung“).

## Entscheidung

Alle Spring-Services laufen mit dem OpenTelemetry-Java-Agent 2.32.0 (`-javaagent`, im Dockerfile per
`JAVA_TOOL_OPTIONS`). Der Code hängt nur von `opentelemetry-api` 1.66.0 ab und nutzt `GlobalOpenTelemetry`;
kein SDK, keine Exporter im Code. Export und Sampling konfiguriert ausschließlich der Agent über `OTEL_*`.

## Konsequenzen

### Positiv
- HTTP-, JDBC-, Log- und JVM-Telemetrie sowie Kontext-Propagation ohne Code.
- Telemetrie-Ziele sind reine Betriebskonfiguration (siehe [08-04](../08-Konzepte/08-04-Konfiguration.md)).

### Negativ
- API-Version muss zur Agent-Version passen; nur gemeinsam ändern (`otelApiVersion` in `build.gradle.kts`).
- Ohne Agent (z. B. `./gradlew bootRun` ohne `-javaagent`) bleibt alles stumm (No-op).
- Der Start ohne Docker muss den Agent selbst laden (geplant in `run-local.sh`).

### Neutral
- Weitere Begründung (z. B. Abwägung gegen Spring-Boot-OTel-Starter/Micrometer): <!-- TODO: Bitte ergänzen -->
