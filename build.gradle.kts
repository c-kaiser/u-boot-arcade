plugins {
    kotlin("jvm") version "2.2.21" apply false
    kotlin("plugin.spring") version "2.2.21" apply false
    id("org.springframework.boot") version "3.5.16" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

// Muss zur Version des OTel-Java-Agents passen (Agent 2.32.0 bündelt API 1.66.0)
val otelApiVersion = "1.66.0"

subprojects {
    group = "de.innoq.uboot"
    version = "1.0.0"

    repositories { mavenCentral() }

    apply(plugin = "org.jetbrains.kotlin.jvm")

    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
        jvmToolchain(21)
        compilerOptions { freeCompilerArgs.add("-Xjsr305=strict") }
    }

    tasks.withType<Test> { useJUnitPlatform() }

    // Alle Spring-Boot-Services teilen sich dieses Setup
    if (name != "game-core") {
        apply(plugin = "org.jetbrains.kotlin.plugin.spring")
        apply(plugin = "org.springframework.boot")
        apply(plugin = "io.spring.dependency-management")

        extra["opentelemetry.version"] = otelApiVersion

        dependencies {
            "implementation"(project(":game-core"))
            "implementation"("org.springframework.boot:spring-boot-starter")
            "implementation"("com.fasterxml.jackson.module:jackson-module-kotlin")
            "implementation"("org.jetbrains.kotlin:kotlin-reflect")
            // Nur die API – Implementierung und Export liefert der OTel-Java-Agent zur Laufzeit
            "implementation"("io.opentelemetry:opentelemetry-api:$otelApiVersion")
            "testImplementation"("org.springframework.boot:spring-boot-starter-test")
            "testImplementation"("org.jetbrains.kotlin:kotlin-test-junit5")
            "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
        }
    }
}
