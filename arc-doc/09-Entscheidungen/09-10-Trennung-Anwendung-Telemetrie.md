# ADR-10: Trennung von Anwendung und Telemetrie, „ohne Telemetrie“ per Exporter `none`

**Status:** accepted (umgesetzt für `run-local.sh`; eigenes Compose für die Telemetrie-Services offen, #5)

**Datum:** 2026-10-22

## Kontext

Das Spiel soll lokal, in GitHub Codespaces und im Workshop ohne Docker starten (F11) und auch dann laufen, wenn kein
Telemetrie-Backend (SigNoz, Collector, Langfuse) gestartet oder erreichbar ist (F12, QS-12). Wohin die Telemetrie
geht, unterscheidet sich je Umgebung: nirgendwohin, lokal (SigNoz/Collector auf dem eigenen Rechner) oder gehostet
(z. B. SigNoz im Workshop, mit Zugangsdaten).

Alle Services laufen mit dem OTel-Java-Agent (ADR-01). Mit OTLP-Exporter und nicht erreichbarem Ziel startet der
Agent normal und blockiert nicht, verwirft die Daten aber und loggt bei jedem Exportversuch Fehler
(`ERROR … GrpcExporter - Failed to export …`, je Signal und Exportintervall, mit Stacktrace).

## Entscheidung

- **Telemetrie-Ziele sind reine Konfiguration** in `.env` (`OTEL_*`-Variablen des Agents, aufbauend auf ADR-01). Weder
  Code noch `run-local.sh`/`stop-local.sh` enthalten Telemetrie-Adressen.
- **Drei Telemetrie-Betriebsarten**, als Blöcke in `.env.example`: *ohne Telemetrie*, *lokal*, *bereitgestellt*.
- **„Ohne Telemetrie“** = `OTEL_TRACES_EXPORTER=none`, `OTEL_METRICS_EXPORTER=none`, `OTEL_LOGS_EXPORTER=none`.
  Der Agent bleibt geladen und instrumentiert weiter, exportiert aber nichts. Das ist der Standard in `.env.example`.
- Die lokalen Telemetrie-Services bekommen ein eigenes Docker Compose, getrennt von der Anwendung (#5, offen); ein Compose für die Anwendung gibt es nicht (ADR-11).
- **Geltungsbereich:** `run-local.sh`. Das Helm-Chart (`uboot.otelEnv`) setzt weder Exporter-Schalter
  noch Header und exportiert immer per OTLP an `otel.endpoint`; „ohne Telemetrie“ ist dort nicht vorgesehen.

### Betrachtete Alternativen

- **Ohne `-javaagent` starten:** kein Agent-Download nötig, aber anderes Laufzeitverhalten als mit Telemetrie
  (keine Instrumentierung, API als No-op) und ein zusätzlicher Schalter im Startskript. Verworfen.
- **Nur OTLP-Ziel weglassen:** Der Agent exportiert dann an seinen Standard `localhost:4318` und loggt Fehler. Verworfen.
- **`OTEL_SDK_DISABLED=true`:** schaltet das SDK ganz ab; wirkt wie „ohne Agent“. Verworfen aus demselben Grund.

## Konsequenzen

### Positiv
- Start ohne Backend ohne Fehler-Logs; Wechsel der Telemetrie-Betriebsart nur über `.env` und Neustart (QS-09, QS-12).
- Gleiches Laufzeitverhalten mit und ohne Export: Die Instrumentierung bleibt aktiv.

### Negativ
- Auch ohne Export muss der Agent einmal heruntergeladen werden (GitHub-Releases).
- Ist ein Ziel konfiguriert, aber nicht erreichbar, entstehen Fehler-Logs (nicht nur Warnungen) – das Spiel läuft weiter.

### Neutral
- Im Helm-Chart gelten die Telemetrie-Betriebsarten noch nicht (siehe Geltungsbereich).
