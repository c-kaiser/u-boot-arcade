# Technische Randbedingungen

| Randbedingung | Beschreibung | Hintergrund/Motivation | Verhandelbar |
|--------------|-------------|----------------------|--------------|
| Kein Docker im Workshop | Services laufen im Workshop direkt als JVM-Prozesse im Browser-VS-Code | Vorgabe der Workshop-Umgebung. Folge: LiteLLM und Collector brauchen Alternativen ohne Container (`pip install`, Binary), **geplant**; das Leaderboard nutzt deshalb eine eingebettete H2-Datei-DB (ADR-07) | nein |
| Gehostete Zielsysteme | SigNoz, OpenTelemetry Demo und Langfuse werden vom Veranstalter bereitgestellt; Adressen und Zugangsdaten noch unbekannt <!-- TODO: Bitte ergänzen --> | Folge: Ziele nur per Umgebungsvariablen konfigurierbar, keine Adressen im Code | nein |
| Langfuse-OTLP nur per HTTP | `https://<host>/api/public/otel`, Basic-Auth (Public/Secret Key), Header `x-langfuse-ingestion-version=4`; kein gRPC | Eigenschaft von Langfuse. Folge: Fan-out über Collector (**geplant**) | nein |
| LLM-Zugang | Anthropic und „Heribert“ (Endpunkt-Art unklar, vermutlich OpenAI-kompatibel <!-- TODO: Bitte ergänzen -->) | Vom Veranstalter bereitgestellt. Folge: Gegner-KI spricht OpenAI-kompatible API, LiteLLM übersetzt | teilweise |
| Programmiersprache: Kotlin | 2.2.21, `-Xjsr305=strict` | Gewählt vom Entwickler <!-- ABGELEITET: Aus Code erkannt, bitte verifizieren --> | ja |
| Framework: Spring Boot | 3.5.16 für alle Services außer `game-core` | Gewählt vom Entwickler | ja |
| Laufzeitumgebung: JDK 21 | Kompiliert mit `jvmTarget 21` und jedem installierten JDK ≥ 21 (ADR-08) | Virtuelle Threads (JDK 21); Workshop-Umgebung hat evtl. anderes JDK | ja |
| Build: Gradle Wrapper 9.6.0 | Multi-Projekt-Build, Wrapper lädt Gradle selbst | Keine lokale Gradle-Installation nötig | ja |
| Netzwerkzugriff beim ersten Build | `services.gradle.org`, `plugins.gradle.org`, Maven Central | Wrapper, Gradle-Plugins, Abhängigkeiten | nein |
| OpenTelemetry | Java-Agent 2.32.0 zur Laufzeit; im Code nur `opentelemetry-api` 1.66.0 über `GlobalOpenTelemetry` | API-Version muss zur Agent-Version passen, nur gemeinsam ändern (AGENTS.md) | nein |
| Ports | game-server 8080, physics 8081, leaderboard 8082, enemy-ai 8083 (je `PORT`) | SigNoz-UI nutzt ebenfalls 8080: lokal Game-Server auf `PORT=8088` legen | ja |
| Zielumgebungen | Workshop-VS-Code, GitHub Codespaces (Dev Container mit JDK 21 + Docker-in-Docker), Sandbox, lokal (macOS/Linux) | Alle müssen mit derselben Konfiguration über Umgebungsvariablen auskommen | nein |
