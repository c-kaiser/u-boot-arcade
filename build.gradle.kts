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

    // Bytecode und API-Stand Java 21 – kompilieren geht mit jedem JDK ab 21, ohne Toolchain-Download
    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
            freeCompilerArgs.addAll("-Xjsr305=strict", "-Xjdk-release=21")
        }
    }
    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    tasks.withType<JavaCompile> { options.release.set(21) }

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
