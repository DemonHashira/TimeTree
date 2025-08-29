plugins {
    kotlin("jvm") version "2.2.10"
    application
}

group = "dev.kamisama"
version = "1.0-SNAPSHOT"

repositories { mavenCentral() }

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

tasks.test { useJUnitPlatform() }

kotlin {
    jvmToolchain(21)
}
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaExec>().configureEach {
    mainClass.set("dev.kamisama.MainKt")
}

application {
    mainClass.set("dev.kamisama.MainKt")
}
