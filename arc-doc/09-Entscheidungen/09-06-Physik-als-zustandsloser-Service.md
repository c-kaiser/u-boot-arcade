# ADR-06: Physik als zustandsloser, separater Service

**Status:** accepted (umgesetzt)

**Datum:** 2026-10-22 <!-- ABGELEITET: Datum des Commits, in dem die Entscheidung im Code sichtbar wird -->

## Kontext

Der Workshop braucht verteilte Traces mit einem Downstream-Aufruf pro Tick, an dem sich Latenz und Fehler gut
injizieren lassen.

## Entscheidung

Der Game-Server schickt pro Tick die komplette `World` plus Steuerbefehle und Chaos an `POST /api/step`; physics
liefert die nächste `World` und Ereignisse zurück, ohne eigenen Zustand.

## Konsequenzen

### Positiv
- Beliebig horizontal skalierbar (`physics.replicas` im Helm-Chart).
- Jeder Tick-Trace enthält einen echten Service-Hop; Chaos „Physik-Latenz/-Fehlerrate“ wirkt direkt sichtbar.

### Negativ
- Netzwerk-Hop und Serialisierung des gesamten Spielstands im 50-ms-Budget.
- Fällt physics aus, friert die Welt ein (siehe [08-05](../08-Konzepte/08-05-Fehlerbehandlung.md)).

### Neutral
- Fachlich wäre eine In-Process-Physik einfacher; die Trennung dient dem Workshop-Zweck. <!-- ABGELEITET: Aus Architektur und Kommentaren erschlossen, bitte verifizieren -->
