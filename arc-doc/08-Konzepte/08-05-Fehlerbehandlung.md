# Fehlerbehandlung und Fallbacks

## Problemstellung

Der Tick-Loop darf nie stehen bleiben, und Störungen in Nachbarservices sollen sichtbar werden, ohne das Spiel
zu beenden – sonst gäbe es im Workshop nichts mehr zu beobachten.

## Lösung: degradieren statt abbrechen

| Situation | Verhalten | Telemetrie |
|---|---|---|
| Unerwarteter Fehler im Tick | `safeTick` fängt `Throwable`, loggt, nächster Tick läuft | Log `Tick fehlgeschlagen` |
| Physik nicht erreichbar/503/Timeout (2 s) | Welt bleibt für diesen Tick stehen (`step ?: before`), `physicsOk=false` an Clients | `game.physics.failures`, `game.tick` Status ERROR, Warn-Log nur beim Übergang |
| Gegner-KI langsam | Entscheidung asynchron (4 Threads), höchstens eine Anfrage pro Gegner gleichzeitig; alte Steuerung gilt weiter | `game.enemy.decision.latency` |
| Gegner-KI nicht erreichbar | Gegner behält letzte bzw. neutrale Steuerung | `game.enemy.decisions{source=none,outcome=unreachable}` |
| LLM aus/Timeout (4 s)/HTTP-Fehler/unparsbare Antwort | enemy-ai fällt auf regelbasierten `BotBrain` zurück | `enemy_ai.decisions{source=fallback,outcome=disabled|timeout|connection_error|http_<code>|parse_error|invalid_response}`, Span-Status ERROR |
| Leaderboard langsam/Fehler | Einträge asynchron (2 Threads, Queue 200, `AbortPolicy`); bei voller Queue verworfen | `game.leaderboard.dropped{reason=queue_full|error}` |
| Leaderboard beim Lesen nicht erreichbar | `GET /api/leaderboard` liefert 502 mit leerer Liste | HTTP-Span (Agent) |
| Langsamer WebSocket-Client | nur der neueste Frame wird gesendet, ältere verworfen; Senden über virtuelle Threads, `ConcurrentWebSocketSessionDecorator` (2 s Sendelimit, 1 MB Puffer) | `game.websocket.frames_dropped` |
| Schwarm-Verbindung bricht ab | Schwarm-Client verbindet sich neu (`reconnectLater`) | `swarm.connects` |

<!-- ABGELEITET: Aus Code erkannt (GameLoop.kt, GameWebSocketHandler.kt, GameServerApplication.kt, EnemyAiApplication.kt, LlmClient.kt), bitte verifizieren -->

## Regeln

- Timeouts sind pro Downstream explizit gesetzt (Connect 2 s; Read: Physik 2 s, Gegner-KI 90 s wegen Runaway, Leaderboard 10 s).
- Kein Retry: ein verpasster Tick wird durch den nächsten ersetzt.
- Eingaben werden begrenzt statt abgelehnt (`GameSettings.sanitized()`, `Controls` auf −1..1, RTT nur 0–60 000 ms).

## Alternativen

Circuit Breaker/Retry-Bibliotheken wurden nicht eingesetzt. <!-- TODO: Bitte ergänzen, ob bewusst (Störungen sollen ungefiltert sichtbar sein) -->
