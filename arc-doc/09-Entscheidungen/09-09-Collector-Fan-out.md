# ADR-09: Lokaler OTel Collector als Fan-out an SigNoz und Langfuse

**Status:** proposed (geplant, nicht umgesetzt)

**Datum:** <!-- TODO: Bitte ergänzen -->

## Kontext

SigNoz soll alle Signale erhalten, Langfuse nur GenAI-Spans. Der Agent exportiert an genau ein OTLP-Ziel.
Langfuse nimmt OTLP **nur per HTTP** an (`https://<host>/api/public/otel`, Basic-Auth mit Public/Secret Key,
`x-langfuse-ingestion-version=4`). Ohne Docker kommt nur ein Binary (otelcol-contrib) in Frage.

## Entscheidung (vorgeschlagen)

Alle Services exportieren an einen lokalen Collector; zwei Pipelines: alles → SigNoz, gefilterte GenAI-Spans
(enemy-ai/litellm) → Langfuse per OTLP/HTTP.

## Konsequenzen

### Positiv
- Services bleiben unverändert (nur `OTEL_EXPORTER_OTLP_ENDPOINT` zeigt auf den Collector).
- Ort für Sampling/Filter (z. B. Tail-Sampling) außerhalb der Anwendung.

### Negativ
- Ein weiterer Prozess pro Teilnehmenden; Filterregeln für „GenAI-Spans“ müssen gepflegt werden.
- Langfuse braucht zusätzlich Prompt/Completion-Attribute (siehe [08-02](../08-Konzepte/08-02-Telemetrie.md)).
