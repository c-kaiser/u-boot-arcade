# Chaos und Störungen

## Problemstellung

Im Workshop sollen Störungen gezielt ausgelöst und ihre Folgen in SigNoz/Langfuse wiedergefunden werden.

## Heute umgesetzt: globale Schalter

Das Wertobjekt `Chaos` (`game-core/.../Model.kt`) ist Teil von `GameSettings`. Es wird im Admin-Panel
(`admin.html` → `PUT /api/admin/settings`) gesetzt, im `SettingsStore` (atomare Referenz, im Speicher) gehalten,
mit `sanitized()` begrenzt und vom Game-Server **pro Aufruf an die Downstream-Services durchgereicht**:

| Schalter | Wirkt in | Transport | Grenze (`sanitized`) |
|---|---|---|---|
| `physicsLatencyMs` | physics (`Thread.sleep`, Span `chaos latency`) | `StepRequest.chaos` | 0–1500 ms (unter dem 2-s-Physik-Timeout) |
| `physicsErrorRate` | physics (HTTP 503) | `StepRequest.chaos` | 0–1 |
| `boilerCoolingBroken`, `hullLeak`, `current` | Physik-Simulation | `StepRequest.chaos` | `current` −10..10 |
| `leaderboardSlowMs` | leaderboard (`SELECT pg_sleep(...)` vor dem Insert) | HTTP-Header `X-Chaos-Slow-Ms` | 0–10 000 ms |
| `aiLatencyMs` | enemy-ai (`Thread.sleep`) | `DecideRequest.chaos` | 0–10 000 ms |
| `aiRunaway` | enemy-ai: 8 LLM-Aufrufe pro Entscheidung, Kontext wächst | `DecideRequest.chaos` | – |
| `gameServerMemoryLeak` | game-server: 64 KB pro Tick (≈ 1,3 MB/s), Freigabe beim Ausschalten | lokal | – |

Sichtbarkeit: aktive Störungen stehen als `chaosActive` in jedem State-Frame (UI-Banner), physics zählt
`chaos.injected` (`chaos.kind`), `game.tick` trägt `chaos.leaked_snapshots`. Der Zustand ist nicht persistent;
ein Neustart des Game-Servers setzt die Schalter auf Startwerte zurück.

## Zielbild (geplant, Umsetzung offen)

In der UI lässt sich **pro U-Boot** eine Störung auf einer **Skala** einstellen; je nach Stärke löst sie gezieltes
Chaos aus (z. B. zusätzliche Latenz). <!-- TODO: Bitte ergänzen – Datenmodell (Chaos pro Sub-ID?), Wirkort, Telemetrie-Attribute (z. B. sub.name am Chaos-Span), Abgrenzung zu den globalen Schaltern -->

## Bekannte Lücken

- `pg_sleep` gibt es nur in Postgres; mit dem geplanten H2-Profil braucht es eine Alternative
  (z. B. `Thread.sleep` oder H2-Äquivalent), siehe [ADR-07](../09-Entscheidungen/09-07-H2-lokal-statt-Postgres.md).
- `aiRunaway` gegen Cloud-Modelle erzeugt echte Kosten, siehe [08-06](08-06-LLM-Kostenkontrolle.md).
