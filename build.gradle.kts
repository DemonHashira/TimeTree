plugins {
    kotlin("jvm") version "2.2.10"
    application
    id("org.jlleitschuh.gradle.ktlint") version "12.1.0"
    id("com.gradleup.shadow") version "9.1.0"
}

group = "dev.kamisama"
version = "1.0-SNAPSHOT"

repositories { mavenCentral() }

dependencies {
    implementation("com.github.ajalt.clikt:clikt:5.0.3")
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
    testImplementation("io.kotest:kotest-property:5.9.1")
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

tasks.shadowJar {
    archiveBaseName.set("timetree")
    archiveClassifier.set("")
    archiveVersion.set("")
}

// Create 'tt' wrapper script for shadow JAR
tasks.register("createTtWrapper") {
    description = "Creates 'tt' wrapper script for the shadow JAR"
    dependsOn("shadowJar")

    doLast {
        val jarFile = file("${layout.buildDirectory.get()}/libs/timetree.jar")
        val ttScript = file("${layout.buildDirectory.get()}/libs/tt")

        if (jarFile.exists()) {
            val scriptContent = """#!/bin/sh
            # TimeTree 'tt' wrapper script
            # This script runs the timetree.jar file

            JAR_FILE="$(dirname "$0")/timetree.jar"
            exec java -jar "${'$'}JAR_FILE" "$@"
            """
            ttScript.writeText(scriptContent)
            ttScript.setExecutable(true)
        }
    }
}

tasks.named("shadowJar") {
    finalizedBy("createTtWrapper")
}
