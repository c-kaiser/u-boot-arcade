# ADR-08: Gradle Wrapper und foojay-Toolchain-Resolver

**Status:** accepted (umgesetzt; vollständiger Build der Spring-Services noch nicht verifiziert)

**Datum:** 2026-10-22 <!-- ABGELEITET: Commit f312f45 -->

## Kontext

Workshop-VS-Code, Codespaces und lokale Rechner haben unterschiedliche JDKs; Docker ist im Workshop nicht verfügbar.

## Entscheidung

Gradle Wrapper (Gradle 9.6.0) im Repo; `settings.gradle.kts` nutzt `org.gradle.toolchains.foojay-resolver-convention`
1.0.0, alle Module kompilieren mit `jvmToolchain(21)`. Gradle selbst läuft mit jedem JDK ab 17.

## Konsequenzen

### Positiv
- Nur ein JDK ≥ 17 auf dem `PATH` nötig; Gradle und JDK 21 lädt der Build selbst.
- Gleiche Gradle-Version lokal und im Dockerfile.

### Negativ
- Erster Build braucht Netz zu `services.gradle.org`, Maven Central und `api.foojay.io` (Firewalls im Workshop?).
