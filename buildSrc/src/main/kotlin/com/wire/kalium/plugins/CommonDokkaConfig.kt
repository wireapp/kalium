package com.wire.kalium.plugins

import org.gradle.api.Project
import org.jetbrains.dokka.DokkaConfiguration.Visibility
import org.jetbrains.dokka.gradle.AbstractDokkaLeafTask

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

/**
 * Adds a common Dokka configuration for the module, including:
 * - $MODULE_DIR$/module.md file
 * - Support of saples `samples` module
 * - Documentation of public and internal entities
 */
fun Project.commonDokkaConfig() {
    if (name !in documentedSubprojects) return

    plugins.apply("org.jetbrains.dokka")
    val rootProject = rootProject
    tasks.withType(AbstractDokkaLeafTask::class.java).configureEach {
        dokkaSourceSets.configureEach {
            file("module.md").takeIf { it.exists() }?.let {
                includes.from(it)
            }
            includes.from(rootProject.file("dokka/moduledoc.md").path)
            samples.from(rootProject.file("samples"))
            includeNonPublic.set(true)
            documentedVisibilities.set(listOf(Visibility.PUBLIC, Visibility.INTERNAL))
        }
    }
}
