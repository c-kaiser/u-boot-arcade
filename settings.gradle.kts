// Lädt das passende JDK für die Toolchain (jvmToolchain(21)) automatisch herunter,
// falls lokal keins installiert ist. Gradle selbst läuft mit jedem JDK ab 17.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "u-boot-arcade"

include(
    "game-core",
    "physics-service",
    "game-server",
    "leaderboard",
    "enemy-ai",
    "player-swarm",
)
