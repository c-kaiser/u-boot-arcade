# ADR-15: LiteLLM aus dem Workshop-Cluster, Modell DeepSeek

**Status:** accepted

**Datum:** 2026-10-30

## Kontext

Bisher sollte das Projekt eine eigene LiteLLM-Instanz mitbringen (`deploy/litellm-config.yaml`, Alias `enemy-brain` →
Anthropic `claude-haiku-4-5`, ADR-05/ADR-14), lokal per `pip install 'litellm[proxy]'` gestartet. Das war nie erprobt
und hätte einen eigenen `ANTHROPIC_API_KEY` gebraucht.

Im Workshop-Cluster läuft bereits ein LiteLLM-Gateway (`http://litellm-service.litellm:4000`). Es bietet unter anderem
`claude-haiku-5-5`, `claude-sonnet-5`, `claude-opus-5-5` und `eu.deepseek-v4.1-flash` an
(`GET /v1/models`, Stand 2026-10-30). Erreichbar ist es nur im Cluster; von außen über einen SSH-Tunnel durch den
Coder-Workspace. Der Workspace selbst ist mit 1,5 GB RAM zu klein, um die Anwendung dort zu bauen und zu starten.

## Entscheidung

- enemy-ai nutzt das **LiteLLM-Gateway des Workshop-Clusters**. Das Projekt bringt keine eigene LiteLLM-Konfiguration
  mehr mit; `deploy/litellm-config.yaml` entfällt.
- Modell ist **`eu.deepseek-v4.1-flash`**, direkt als `LLM_MODEL` (Default in `application.yaml` und Helm-Werten).
  Den Alias `enemy-brain` gibt es nicht mehr.
- Lokal: SSH-Tunnel `ssh -N -L 4000:litellm-service.litellm:4000 coder.<workspace>`, damit passt der Default
  `LLM_BASE_URL=http://localhost:4000/v1`. Im Cluster (Helm): `llm.baseUrl=http://litellm-service.litellm:4000/v1`.

### Betrachtete Alternativen

- **Eigene LiteLLM-Instanz behalten** (ADR-05 in der bisherigen Form): volle Kontrolle über Modelle und Budget,
  aber ein zusätzlicher, nie erprobter Prozess und eigene Anbieter-Keys. Verworfen.
- **Anthropic-Modell aus dem Gateway** (`claude-haiku-5-5`): ebenfalls möglich; entschieden wurde für DeepSeek.
  Ein Wechsel ist nur `LLM_MODEL` (QS-10).

## Konsequenzen

### Positiv
- Kein eigener Gateway-Prozess, keine Anbieter-Keys im Projekt.
- Modellwechsel bleibt reine Konfiguration (`LLM_MODEL`).

### Negativ
- Ohne Tunnel bzw. außerhalb des Workshop-Clusters gibt es kein LLM; enemy-ai fällt dann auf `BotBrain` zurück.
- Budget und Modellliste liegen beim Betreiber des Gateways, nicht im Projekt. Ob das Gateway ein Budget pro
  Teilnehmer setzt, ist offen. Die Kostenbremse in enemy-ai (TD4) bleibt deshalb nötig.
- Die Preise für die Kostenmetrik (`LLM_PRICE_*_PER_MTOK`, Default 3/15 USD) sind nicht auf DeepSeek abgestimmt.
  <!-- TODO: Bitte ergänzen – Preise von eu.deepseek-v4.1-flash -->

### Neutral
- Ersetzt den Gateway-Teil von ADR-05 (eigene `litellm-config.yaml`) und die Modellwahl aus ADR-14.
  „Kein lokales Modell“ (ADR-14) gilt weiter.
