# Organisatorische Randbedingungen

| Randbedingung | Beschreibung | Hintergrund/Motivation |
|--------------|-------------|----------------------|
| Rolle im Workshop | Wir (Christian Kaiser) sind Teilnehmer und bringen die App mit, nicht die Workshop-Leitung | Keine Kontrolle über Workshop-Umgebung, Zielsysteme und LLM-Zugänge; App muss sich anpassen |
| Zeitrahmen | Das Spiel soll vor dem Workshop fertig sein <!-- TODO: Bitte ergänzen (Datum) --> | Fokus auf Lauffähigkeit statt Ausbau; optionale Erweiterungen (z. B. Jev) nachrangig |
| Team | Einzelentwickler, unterstützt durch Coding-Agenten <!-- ABGELEITET: Aus Code erkannt, bitte verifizieren --> | Leitplanken für Agenten in [AGENTS.md](../../AGENTS.md) |
| LLM-Kosten | Veranstalter wünscht, Agenten nicht „durchglühen“ zu lassen; lieber gezielt mit Modellen experimentieren | Kostenbremse **geplant**; keine Schleifen, Lasttests oder Runaway-Chaos gegen Cloud-Modelle ohne Rückfrage; Tests mit `GAME_LLM_ENABLED=false` |
| Datenvolumen | Gehostetes SigNoz wird von vielen Teilnehmenden geteilt | Sampling (`parentbased_traceidratio`) für 20 Traces/s empfohlen |
| Budget | <!-- TODO: Bitte ergänzen (LLM-Budget; ob das Workshop-Gateway eins setzt, ist offen) --> | Grundlage für die geplante Kostenbremse |
