package com.wire.kalium.plugins

import org.gradle.api.Project
import org.jetbrains.dokka.gradle.DokkaTask

val documentedSubprojects = listOf(
    "app",
    "calling",
    "cli",
    "cryptography",
    "logger",
    "logic",
    "network",
    "persistence",
    "protobuf"
)

fun Project.commonDokkaConfig() {
    if (!(name in documentedSubprojects)) return

    plugins.apply("org.jetbrains.dokka")
    val rootProject = rootProject
    tasks.withType(DokkaTask::class.java).configureEach {
        outputDirectory.set(file("build/dokka"))

        dokkaSourceSets.create("common") {
            includes.from(rootProject.file("dokka/moduledoc.md").path)
            includeNonPublic.set(true)
            val samplesPath = project.file("src/samples/kotlin/com/wire/kalium").path
            println("SAMPLES PATH = $samplesPath")
            samples.from(samplesPath)
        }
    }
}
