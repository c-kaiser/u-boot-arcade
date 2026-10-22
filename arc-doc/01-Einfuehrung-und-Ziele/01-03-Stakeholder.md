# Stakeholder

| Rolle | Beschreibung | Erwartung an Architektur |
|-------|-------------|------------------------|
| Entwickler / Mitbringer der App | Christian Kaiser (INNOQ), Teilnehmer des Workshops, bringt das Spiel als „eigene App“ mit | Schnell lauffähig, verständlich, Telemetrie und Chaos leicht erweiterbar; rechtzeitig vor dem Workshop fertig |
| Workshop-Teilnehmende | Spielen, lösen Störungen aus und analysieren die Telemetrie | Spiel startet ohne Docker im Browser-VS-Code; Störungen zeigen klare, auffindbare Spuren in SigNoz und Langfuse |
| Workshop-Veranstalter | Stellt Umgebung (Browser-VS-Code, gehostetes SigNoz, OTel Demo, Langfuse) und LLM-Zugang bereit | Kein Docker nötig; LLMs sparsam einsetzen (Kosten); Datenvolumen an gehostetes SigNoz im Rahmen <!-- TODO: Bitte ergänzen (Ansprechpartner) --> |
| Coding-Agenten | Arbeiten nach [AGENTS.md](../../AGENTS.md) am Code und an dieser Doku | Klare Leitplanken (Branches/PR, Build vor Abschluss, keine teuren LLM-Läufe), aktuelle Doku |
| Betreiber der Zielsysteme | SigNoz, Langfuse, LLM-Anbieter (Anthropic, „Heribert“) | Korrekte OTLP-Formate und Zugangsdaten, begrenzte Last <!-- TODO: Bitte ergänzen --> |

<!-- ABGELEITET: Aus Code erkannt, bitte verifizieren -->
