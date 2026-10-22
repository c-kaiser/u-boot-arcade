# ADR-04: Broadcast pro Client über virtuelle Threads, alte Frames verwerfen

**Status:** accepted (umgesetzt)

**Datum:** 2026-10-22 <!-- ABGELEITET: Datum des Commits, in dem die Entscheidung im Code sichtbar wird -->

## Kontext

Ein langsamer oder hängender WebSocket-Client darf weder den Tick (50 ms) noch andere Clients aufhalten.

## Entscheidung

`SessionRegistry.broadcast` legt pro Client nur den **neuesten** Spielstand ab (`AtomicReference`). Ist noch einer
ungesendet, wird er ersetzt und `game.websocket.frames_dropped` gezählt. Gesendet wird asynchron über einen
Virtual-Thread-Executor; `ConcurrentWebSocketSessionDecorator` (2 s Sendelimit, 1 MB Puffer) serialisiert Sendungen.

## Konsequenzen

### Positiv
- Tick-Dauer unabhängig von der Client-Qualität; langsame Clients bekommen einfach weniger Frames.
- Ruckeln ist messbar (`frames_dropped`, `swarm.state.interval`).

### Negativ
- Kein garantierter Empfang jedes Frames; Ereignisse in verworfenen Frames sieht der Client nicht.

### Neutral
- Setzt JDK 21 (virtuelle Threads) voraus.
