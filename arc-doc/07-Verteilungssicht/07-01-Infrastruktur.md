# Verteilungssicht

<!-- ABGELEITET: Aus Code erkannt (Dockerfile, docker-compose.yaml, deploy/helm, deploy/litellm-config.yaml, README.md), bitte verifizieren -->

## Infrastruktur Ebene 1

Die Anwendung besteht aus fünf Spring-Boot-Prozessen (JDK 21, je ein Fat-JAR aus `./gradlew bootJar`) (das Leaderboard
mit eingebetteter H2-Datei-DB) und optional LiteLLM. Sie wird in drei Varianten betrieben:

| Variante | Status | Zielumgebung | Details |
|----------|--------|--------------|---------|
| Lokal als JVM-Prozesse, ohne Docker | **geplant** | Workshop-VS-Code (Browser), Sandbox, Codespaces | [07-02](07-02-Lokal-ohne-Docker.md) |
| Docker Compose | **umgesetzt** | Lokal (Laptop), Codespaces/Dev Container | [07-03](07-03-Docker-Compose.md) |
| Kubernetes per Helm | **umgesetzt** (nur mit eigenem Renderer geprüft, nicht mit `helm lint`) | Cluster neben SigNoz | [07-04](07-04-Kubernetes-Helm.md) |

Der Telemetrie-Fluss (heute und geplant mit Collector-Fan-out) steht in [07-05](07-05-Telemetrie-Fluss.md).

### Übersichtsdiagramm (variantenunabhängig)

```mermaid
flowchart LR
    U["Browser"] -- "HTTP/WebSocket" --> GS
    subgraph Knoten["Laufzeitumgebung (Prozesse / Container / Pods)"]
        GS["game-server ×1"]
        PH["physics ×n"]
        AI["enemy-ai"]
        LB["leaderboard ×1<br/>mit H2-Datei-DB"]
        SW["player-swarm ×n"]
    end
    SW --> GS
    GS --> PH & AI & LB
    AI --> LL["LiteLLM"] --> M["Modell: Ollama / Anthropic / Heribert"]
    Knoten -. OTLP .-> O["SigNoz (gehostet oder lokal in Docker)"]
```

### Motivation

- **Konfiguration nur über Umgebungsvariablen:** Der Code kennt keine Zieladressen (Downstream-URLs, DB, LLM, OTLP).
  So laufen dieselben Artefakte in allen Varianten.
- **Instrumentierung zur Laufzeit:** Der OTel-Java-Agent 2.32.0 wird per `-javaagent` geladen (im Image über
  `JAVA_TOOL_OPTIONS`), nicht in die JARs eingebaut.
- **Workshop ohne Docker:** Im Workshop gibt es nur einen Browser-VS-Code pro Person; deshalb ist der Start als
  JVM-Prozesse die eigentliche Zielvariante. Docker Compose und Helm dienen lokal bzw. für einen gemeinsamen Cluster.

### Qualitäts- und Performance-Merkmale

- **Skalierung:** `physics-service` ist zustandslos und horizontal skalierbar (Helm Standard 2 Replicas). `player-swarm`
  skaliert die Last über Replicas. `enemy-ai` ist zustandslos (Helm: 1 Replica).
- **Keine Redundanz des Game-Servers:** genau 1 Instanz, da der Spielstand im Speicher liegt; Neustart = Spielstand weg.
- **Persistenz:** nur das Leaderboard, als eingebettete H2-Datei-DB. Ohne Docker unter `./data/`, in Compose auf Volume
  `leaderboard-data`, im Helm-Chart bewusst `emptyDir` (flüchtig). Deshalb genau 1 Leaderboard-Instanz.
- **Verfügbarkeit:** Für einen Workshop ausgelegt, keine Hochverfügbarkeit. <!-- TODO: Bitte ergänzen – Anforderungen an Betrieb während des Workshops -->

### Mapping Software → Infrastruktur

| Software-Baustein | Port | Lokal ohne Docker (geplant) | Docker Compose | Kubernetes (Helm) |
|------------------|------|------------------------------|----------------|-------------------|
| `game-server` | 8080 | JVM-Prozess, `PORT=8088` empfohlen (Konflikt mit SigNoz-UI auf 8080) | Container, Host-Port `GAME_PORT` (8088) | Deployment 1 Replica, Strategie `Recreate`, Service Port 80, optional Ingress |
| `physics-service` | 8081 | JVM-Prozess | Container `physics` | Deployment, 2 Replicas |
| `leaderboard` | 8082 | JVM-Prozess, H2-Datei unter `./data/` | Container, H2-Datei auf Volume `leaderboard-data` | Deployment 1 Replica, H2-Datei in `emptyDir` |
| `enemy-ai` | 8083 | JVM-Prozess | Container | Deployment 1 Replica, API-Key aus Secret |
| `player-swarm` | – | JVM-Prozess (optional) | Container, Profil `swarm` (10 Spieler) | Deployment, `playersPerPod` 10 |
| LiteLLM | 4000 | `pip install 'litellm[proxy]'` (geplant) | Container, Profil `llm` | nicht im Chart, `llm.baseUrl` zeigt auf externe Instanz |
| Modell | – | Anthropic / Heribert (Cloud) | Ollama-Container `qwen2.5:3b` (Profil `llm`) oder Anthropic | extern |
| OTel Collector | 4317/4318 | `otelcol-contrib`-Binary (geplant) | – | – (SigNoz-Collector im Cluster) |
| OTel-Java-Agent | – | einmal heruntergeladen, per `-javaagent` (geplant) | im Image `/otel/opentelemetry-javaagent.jar` | im Image |

### Knoten-Beschreibungen

#### Container-Image (alle Services)

- **Typ:** Ein gemeinsames Multi-Stage-`Dockerfile`, Build-Argument `SERVICE` wählt das Modul.
- **Build:** `eclipse-temurin:21-jdk`, `./gradlew :<SERVICE>:bootJar -x test`.
- **Laufzeit:** `eclipse-temurin:21-jre`, Nutzer UID 10001, `JAVA_TOOL_OPTIONS=-javaagent:… -XX:MaxRAMPercentage=75`.
- **Images:** lokal `u-boot-arcade/<service>:local`, für den Cluster `<registry>/u-boot-arcade-<komponente>:<tag>` über `deploy/build-images.sh`.
