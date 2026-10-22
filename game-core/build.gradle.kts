// Reine Spiellogik ohne Frameworks: Modell, Physik, Bot-KI, LLM-Prompt/Parsing
dependencies {
    testImplementation(kotlin("test"))
    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
