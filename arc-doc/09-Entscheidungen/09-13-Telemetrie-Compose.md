# ADR-13: Telemetrie-Compose mit Foundry-SigNoz und eigenem Gateway-Collector

**Status:** accepted (umgesetzt, #5)

**Datum:** 2026-10-23

## Kontext

Für die Telemetrie-Betriebsart *lokal* sollen die Telemetrie-Services in einem eigenen Docker Compose laufen,
getrennt von der Anwendung (ADR-10, ADR-11). Die Services exportieren an `localhost:4317`. Später soll ein Collector
zusätzlich GenAI-Spans an Langfuse verteilen (ADR-09, #11).

SigNoz liefert seit v0.130 keine Compose-Dateien mehr, sondern erzeugt sie mit dem Werkzeug Foundry
(`foundryctl forge` aus einer `casting.yaml`). SigNoz bringt einen eigenen Collector („ingester“) mit, der in
ClickHouse schreibt und seine Konfiguration per OpAMP vom SigNoz-Server bekommt.

## Entscheidung

- **SigNoz per Foundry, Ergebnis eingecheckt:** `deploy/telemetry/signoz/` enthält `casting.yaml` und die einmal
  erzeugten Dateien. Gestartet wird mit einfachem `docker compose`, ohne Foundry. Änderungen nur über `casting.yaml`
  und neues Erzeugen.
- **Eigener Gateway-Collector** (`otel/opentelemetry-collector-contrib`) als einziger OTLP-Eingang am Host
  (4317/4318); der Ingester von SigNoz bekommt keine Host-Ports. Der Gateway ergänzt `deployment.environment` aus
  `deployment.environment.name` (SigNoz filtert nach dem älteren Attribut).
- **Schlank:** Metadaten von SigNoz in SQLite statt Postgres, ClickHouse Keeper (Foundry-Standard) statt ZooKeeper.
- **Festes lokales Admin-Konto** (`SIGNOZ_USER_ROOT_*`), weil SigNoz ohne Organisation seinem Collector nur eine leere
  Konfiguration gibt. Alle Host-Ports nur an `127.0.0.1`, weil das Passwort im Repo steht.

### Betrachtete Alternativen

- **Ingester von SigNoz direkt an 4317:** ein Container weniger, aber für den Fan-out an Langfuse (#11) müsste man
  Ports und Struktur später umbauen. Verworfen.
- **Alte SigNoz-Compose-Dateien (bis v0.129):** ohne Foundry, aber veraltet und nicht mehr gepflegt. Verworfen.
- **Foundry bei jedem Start (`foundryctl cast`):** immer aktuell, aber ein zusätzliches Werkzeug für alle. Verworfen.
- **Konto in der UI anlegen lassen:** Bis dahin nimmt SigNoz keine Daten an. Verworfen.

## Konsequenzen

### Positiv
- Für die Services ändert sich nichts (`localhost:4317`); Langfuse kommt später als zweite Pipeline dazu.
- Reproduzierbar: alle Images mit fester Version, kein Werkzeug außer Docker nötig.

### Negativ
- Feste globale Namen aus Foundry (Container `signoz-*`, Netz `signoz-network`, Volumes `signoz-*`) kollidieren mit
  anderen lokalen SigNoz-Installationen (R16).
- Ein Init-Container lädt bei jedem Start `histogram-quantile` von GitHub; ohne Netz startet SigNoz nicht.
- Beim Aktualisieren muss neu erzeugt und der Unterschied geprüft werden.
