# ADR-07: Leaderboard lokal mit H2 statt Postgres

**Status:** proposed (geplant, nicht umgesetzt)

**Datum:** <!-- TODO: Bitte ergänzen -->

## Kontext

Im Workshop gibt es kein Docker, also auch kein Postgres-Container. Das Leaderboard nutzt heute Postgres
(`DB_URL`, `schema.sql` mit `BIGSERIAL`/`TIMESTAMPTZ`) und für Chaos `SELECT pg_sleep(...)`.

## Entscheidung (vorgeschlagen)

Spring-Profil `local` mit H2 als Datei-DB. Schema H2-kompatibel halten (`BIGSERIAL`/`TIMESTAMPTZ` anpassen),
DB-Chaos per `Thread.sleep` oder H2-Äquivalent.

## Konsequenzen

### Positiv
- Start ohne externe Datenbank; JDBC-Spans bleiben (Agent instrumentiert auch H2).

### Negativ
- Zwei DB-Dialekte; das Chaos „langsame DB“ ist mit `Thread.sleep` kein echter DB-Span mehr.
- Die H2-Abhängigkeit fehlt noch im Build.
