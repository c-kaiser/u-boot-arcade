# ADR-08: Gradle Wrapper und jvmTarget 21 ohne Toolchain-Download

**Status:** accepted (umgesetzt, `./gradlew build` erfolgreich)

**Datum:** 2026-10-22

## Kontext

Workshop-VS-Code, Codespaces, Sandbox und lokale Rechner haben unterschiedliche JDKs; Docker ist im Workshop nicht verfügbar.
Die Netzwerkfreigaben im Workshop sind unbekannt – jeder zusätzliche Download-Host ist ein Risiko.

## Entscheidung

Gradle Wrapper (Gradle 9.6.0) im Repo. Statt einer Gradle-Toolchain (`jvmToolchain(21)`) wird nur das Ziel festgelegt:
Kotlin `jvmTarget = 21` mit `-Xjdk-release=21`, Java `release = 21`. Kompiliert wird mit dem installierten JDK (ab 21).

## Betrachtete Alternativen

- **Toolchain mit foojay-Resolver:** lädt JDK 21 automatisch, wenn keins installiert ist. Verworfen: zusätzliche Hosts
  (`api.foojay.io`, JDK-Downloads) und ca. 200 MB Download beim ersten Build.
- **Toolchain ohne Resolver:** verlangt exakt JDK 21 auf jedem Rechner – zu streng (Sandbox hat JDK 25).

## Konsequenzen

### Positiv
- Ein JDK ab 21 auf dem `PATH` genügt; kein JDK-Download.
- Bytecode und genutzte JDK-API bleiben auf Java 21, auch beim Kompilieren mit neuerem JDK.
- Gleiche Gradle-Version lokal und im Dockerfile.

### Negativ
- Erster Build braucht Netz zu `services.gradle.org` (leitet auf `release-assets.githubusercontent.com` weiter),
  `plugins.gradle.org` und Maven Central.
- Getestet wird mit dem installierten JDK, nicht zwingend mit 21.
