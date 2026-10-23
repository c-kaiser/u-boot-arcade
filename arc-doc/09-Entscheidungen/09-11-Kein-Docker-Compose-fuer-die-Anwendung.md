# ADR-11: Kein Docker Compose für die Anwendung

**Status:** accepted (umgesetzt)

**Datum:** 2026-10-22

## Kontext

Bisher gab es drei Verteilungsvarianten: JVM-Prozesse ohne Docker (geplant), Docker Compose (Anwendung samt
LiteLLM/Ollama im Profil `llm`) und Kubernetes per Helm. Mit `run-local.sh` (#3) läuft die Anwendung lokal und in
GitHub Codespaces ohne Docker und ohne weitere Infrastruktur. Im Workshop läuft sie entweder in einem Codespace mit
Start-/Stoppskript oder – falls genutzt – im Kubernetes-Cluster des Workshops.

Das Compose der Anwendung war dafür überflüssig, musste aber parallel gepflegt werden (eigene Umgebungsvariablen,
`host.docker.internal` statt `localhost` in derselben `.env`, eigene Telemetrie-Standards).

## Entscheidung

- Die Anwendung hat nur noch zwei Verteilungsvarianten: **ohne Docker** (`run-local.sh`/`stop-local.sh`, lokal und in
  Codespaces) und **Kubernetes per Helm**.
- `docker-compose.yaml` der Anwendung entfällt. `Dockerfile` und `deploy/build-images.sh` bleiben für die Helm-Images.
- Telemetrie-Services haben ein **eigenes** Compose, getrennt von der Anwendung (`deploy/telemetry`, #5: SigNoz und Gateway-Collector, ADR-13; Langfuse folgt mit #11).
- LiteLLM und Ollama gehören nicht mehr zum Repository-Start: Für die Gegner-KI mit LLM zeigt `LLM_BASE_URL` auf eine
  LiteLLM-Instanz (lokal per `pip`, geplant, oder extern). Standard ist `http://localhost:4000/v1`.

### Betrachtete Alternativen

- **Compose behalten:** bequemer Start von LiteLLM und Ollama, aber eine dritte Variante mit eigenen Fallstricken,
  die im Workshop niemand nutzt. Verworfen.

## Konsequenzen

### Positiv
- Eine `.env` für genau ein Startverfahren; kein `localhost`/`host.docker.internal`-Unterschied mehr.
- Weniger zu pflegen und zu dokumentieren.

### Negativ
- Der Ein-Kommando-Start von LiteLLM und Ollama entfällt. Für LLM-Experimente muss LiteLLM separat laufen
  (`pip install 'litellm[proxy]'`, `deploy/litellm-config.yaml`), Ollama nativ.
- Wer Docker nutzen will, muss die Images selbst starten (`deploy/build-images.sh`) oder Helm verwenden.

### Neutral
- Das Container-Image bleibt unverändert (Helm).
