# ADR-03: Game-Server mit einer Replica, Spielstand im Speicher

**Status:** accepted (umgesetzt)

**Datum:** 2026-10-22 <!-- ABGELEITET: Datum des Commits, in dem die Entscheidung im Code sichtbar wird -->

## Kontext

Der Spielstand wird 20-mal pro Sekunde fortgeschrieben und an alle Clients verteilt. Ein Workshop-Spiel braucht
keine Hochverfügbarkeit.

## Entscheidung

Der Game-Server hält `World`, Steuerbefehle, Einstellungen und Chaos-Schalter nur im Speicher und läuft mit genau
einer Replica (README, Helm). Skaliert werden stattdessen Physik und Lastgenerator.

## Konsequenzen

### Positiv
- Einfache Tick-Schleife ohne verteilten Zustand oder Synchronisation.

### Negativ
- Single Point of Failure: Neustart beendet alle Runden und setzt Admin-/Chaos-Einstellungen zurück.
- Keine horizontale Skalierung des WebSocket-Fan-outs; die Last pro Tick wächst mit der Clientzahl.
