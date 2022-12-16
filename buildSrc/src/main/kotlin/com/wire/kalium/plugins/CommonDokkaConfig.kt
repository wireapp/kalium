package com.wire.kalium.plugins

import org.gradle.api.Project
import org.jetbrains.dokka.gradle.DokkaTask

val documentedSubprojects = listOf(
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

        val targetSourceSet = dokkaSourceSets.findByName("commonMain") ?: dokkaSourceSets.findByName("main")
        targetSourceSet?.run {
            includes.from(rootProject.file("dokka/moduledoc.md").path)
            samples.from(rootProject.file("samples/src/main/kotlin/com/wire/kalium"))
            includeNonPublic.set(true)
        }
    }
}
