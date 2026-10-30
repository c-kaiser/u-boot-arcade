# Laufzeitsicht

<!-- ABGELEITET: Aus Code erkannt (GameLoop, GameWebSocketHandler, SessionRegistry, DecideController, LlmClient, ScoreController, PhysicsApplication, game.js), bitte verifizieren -->

Die Szenarien verwenden die Bausteine aus Kapitel 5. Beschrieben ist der **umgesetzte** Stand; Geplantes ist markiert.

## Szenario 1: Spieler verbindet sich per WebSocket

### Beschreibung

Ein Browser (oder ein `player-swarm`-Spieler) öffnet `/ws`, tritt mit einem Namen bei und erhält ab dann pro Tick
den Spielstand. Das Boot erscheint erst im nächsten Tick in der Welt.

### Ablauf

```mermaid
sequenceDiagram
    participant B as Browser (game.js)
    participant H as GameWebSocketHandler
    participant R as SessionRegistry
    participant L as GameLoop

    B->>H: WebSocket öffnen /ws?client=browser
    H->>R: add(session, kind=browser)
    B->>H: {"type":"join","name":"Christian"}
    H->>L: join(name) → Sub-ID p-xxxxxxxx in Join-Queue
    H-->>B: {"type":"welcome","id":"p-…"}
    Note over L: nächster Tick: reconcileRoster übernimmt das Boot
    loop alle 50 ms
        B->>H: {"type":"input","throttle","dive","fire"}
        H->>L: setControls(subId, Controls)
    end
    loop jede Sekunde
        B->>H: {"type":"ping","t"}
        H-->>B: {"type":"pong","t"}
        B->>H: {"type":"rtt","ms"} → Metrik game.client.rtt
    end
    B--xH: Verbindung getrennt
    H->>R: remove(session)
    H->>L: leave(subId) → Boot im nächsten Tick entfernt
```

### Besonderheiten

- Der Client-Typ (`browser`/`swarm`) kommt aus dem Query-Parameter und wird als `client.kind` an Metriken gehängt.
- Spielstände erhält nur ein Client mit `subId` (also nach `join`). Zuschauer ohne Beitritt bekommen keine `state`-Nachrichten.
- `game.js` verbindet sich nach Abbruch nach 2 s neu und sendet `join` erneut (neues Boot, Punkte der Runde verloren).
- Es gibt keine Authentifizierung; Name wird auf 16 Zeichen gekürzt.

---

## Szenario 2: Ein Spiel-Tick

### Beschreibung

Der `game-tick`-Thread führt alle 50 ms einen Tick aus. Jeder Tick ist ein eigener Trace `game.tick` (ohne Parent).
Synchron ist nur der Physik-Aufruf; Gegner-KI und Leaderboard laufen asynchron, der Broadcast wird an virtuelle Threads abgegeben.

### Ablauf

```mermaid
sequenceDiagram
    participant T as GameLoop (game-tick)
    participant E as Gegner-Executor
    participant P as physics-service
    participant Q as Leaderboard-Executor
    participant R as SessionRegistry
    participant C as Clients

    T->>T: Span game.tick starten, reconcileRoster (Join/Leave, Bots, Gegner)
    opt alle 20 Ticks
        T->>E: requestEnemyDecisions (async, Szenario 3)
    end
    T->>T: Bots: BotBrain.decide, Gegner: letzte enemyControls
    T->>P: POST /api/step (Welt, Controls, dt, Chaos)
    alt Erfolg
        P-->>T: StepResponse (neue Welt, Ereignisse)
    else Fehler/Timeout (2 s)
        P--xT: 503 oder Timeout
        T->>T: Welt bleibt, Span ERROR, game.physics.failures++
    end
    T->>T: handleEvents: Logs, game.events, bei DESTROYED → Q (Szenario 4)
    T->>R: broadcast(state-JSON) in Span game.broadcast
    R-)C: pro Client neuester Frame (virtueller Thread)
    T->>T: Span beenden, game.tick.duration, ggf. game.tick.overruns
```

### Besonderheiten

- `scheduleAtFixedRate` mit einem Thread: dauert ein Tick länger als 50 ms, verschieben sich Folgeticks (Overrun, Ruckeln).
- Ausnahmen im Tick werden geloggt; die Schleife läuft weiter (`safeTick`).
- Hat ein Client den letzten Frame noch nicht abgenommen, wird er überschrieben (`game.websocket.frames_dropped`).
- 20 Ticks/s erzeugen 20 Traces/s; Sampling ist über `OTEL_TRACES_SAMPLER` konfigurierbar (Standard: `parentbased_always_on`).

---

## Szenario 3: Gegner-Entscheidung mit LLM und Fallback

### Beschreibung

Alle `GAME_ENEMY_DECISION_EVERY_TICKS` (Standard 20 ≈ 1 s) fragt der Game-Server für jeden lebenden Gegner
asynchron die `enemy-ai`. Pro Gegner läuft höchstens eine Anfrage. Die Antwort gilt ab dem nächsten Tick.

### Ablauf

```mermaid
sequenceDiagram
    participant E as GameLoop / Gegner-Executor
    participant D as DecideController
    participant C as LlmClient
    participant X as LiteLLM → Modell
    participant B as BotBrain

    E->>D: POST /api/decide (enemyId, Welt, useLlm, Chaos)
    opt Chaos aiLatencyMs > 0
        D->>D: Thread.sleep
    end
    alt useLlm = false
        D->>B: decide → source=fallback, outcome=disabled
    else useLlm = true
        D->>D: Prompt aus LlmProtocol
        D->>C: chat(messages) [Runaway-Chaos: 8 Runden]
        C->>X: POST /chat/completions (Span chat eu.deepseek-v4.1-flash)
        alt Antwort ok und JSON parsbar
            X-->>C: Text + usage
            C-->>D: ChatResult (Tokens, Kosten als Metrik)
            D->>D: source=llm, outcome=ok
        else Timeout / Verbindungs- / HTTP-Fehler
            C--xD: LlmException(errorType)
            D->>B: decide → source=fallback, outcome=timeout|connection_error|http_xxx
        else Antwort nicht parsbar
            D->>B: decide → source=fallback, outcome=parse_error
        end
    end
    D-->>E: DecideResponse (Controls, source, outcome, llmCalls)
    E->>E: enemyControls[id] = Controls, game.enemy.decision.latency
```

### Besonderheiten

- Ist `enemy-ai` nicht erreichbar, behält der Gegner seine letzte Steuerung (`outcome=unreachable` im Game-Server).
- Trace-Kontext wird vom `game.tick` in den Executor übernommen (`Context.wrap`); `traceparent` geht bis LiteLLM.
- Timeouts: LLM `LLM_TIMEOUT_MS` (4 s, Helm 8 s) je Aufruf, Game-Server → enemy-ai 90 s.
- **Geplant:** Deckelung des Runaway-Chaos, Langfuse-Attribute (Prompt/Completion, Session-ID).

---

## Szenario 4: Runde endet → Leaderboard

### Beschreibung

Wird ein Boot zerstört (Torpedo, Zerstörungstiefe, Überhitzung, …), meldet die Physik ein `DESTROYED`-Ereignis.
Der Game-Server trägt die Runde asynchron ins Leaderboard ein; die Anzeige im Browser fragt alle 5 s die Rangliste ab.

### Ablauf

```mermaid
sequenceDiagram
    participant T as GameLoop
    participant Q as Leaderboard-Executor (2 Threads, Queue 200)
    participant S as ScoreController
    participant DB as H2 (eingebettet)
    participant B as Browser

    T->>T: DESTROYED-Ereignis → Log „… zerstört durch … – Runde endet“
    T->>Q: submitScore(ScoreEntry)
    alt Queue voll
        Q--xT: abgelehnt → game.leaderboard.dropped (queue_full)
    else angenommen
        Q->>S: POST /api/scores, Header X-Chaos-Slow-Ms
        opt Chaos leaderboardSlowMs > 0
            S->>S: Thread.sleep(…)
        end
        S->>DB: INSERT INTO scores
        S-->>Q: 201 Created (Fehler → game.leaderboard.dropped, error)
    end
    loop alle 5 s
        B->>T: GET /api/leaderboard
        T->>S: GET /api/scores/top?limit=10
        S->>DB: SELECT … GROUP BY name, kind
        S-->>T: Rangliste
        T-->>B: Rangliste (bei Fehler 502 + leere Liste)
    end
```

### Besonderheiten

- Betrifft alle Bootstypen (Spieler, Bots, Gegner); jedes Leben ist eine Runde.
- Die Chaos-Verzögerung liegt im Leaderboard-Prozess vor dem Insert: Im Trace wird der HTTP-Span `POST /api/scores`
  lang, nicht der DB-Span. Variante mit echtem DB-Span siehe [IDEEN.md](../../IDEEN.md).

---

## Szenario 5: Chaos „Physik-Fehlerrate“ und „Physik-Latenz“

### Beschreibung

Im Admin-Panel werden Chaos-Schalter gesetzt. Sie gelten heute **global für alle Boote** und werden mit jedem
Request an die Downstream-Services weitergereicht (im Body bzw. bei Leaderboard als Header).

### Ablauf

1. Admin setzt in `admin.html` „Physik-Fehlerrate“ auf z. B. 0,3 → `PUT /api/admin/settings`.
2. `SettingsStore` begrenzt die Werte (`sanitized`) und loggt die Änderung „Einstellungen geändert: …“.
3. Im nächsten Tick sendet `GameLoop` das `Chaos`-Objekt im `StepRequest` mit; die Clients sehen es in `chaosActive`.
4. `physics-service` wirft bei ~30 % der Steps HTTP 503 (Span-Status ERROR, Metrik `chaos.injected{chaos.kind=physics_error}`, Warn-Log).
5. `GameLoop` zählt `game.physics.failures`, markiert `game.tick` als Fehler; die Welt bleibt in diesem Tick stehen (`physicsOk=false`).
6. Bei „Physik-Latenz“ entsteht stattdessen ein Span `chaos latency` (max. 1500 ms, im Panel bis 500 ms); `game.tick.duration` steigt, `game.tick.overruns` zählt, im Browser sinken Updates/s, beim Schwarm steigt `swarm.state.interval`.
7. Zurücksetzen im Panel beendet die Störung ab dem nächsten Tick.

### Besonderheiten

- Weitere Schalter wirken analog an anderer Stelle: KI-Latenz/Runaway in `enemy-ai`, Leaderboard langsam in `leaderboard`,
  Memory Leak im `game-server` (64 KB je Tick bis zum Abschalten), Kessel/Leck/Strömung in der Physik.
- **Zielbild (geplant):** Störung pro U-Boot auf einer Skala in der UI. Technische Umsetzung offen. <!-- TODO: Bitte ergänzen -->
