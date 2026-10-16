/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.plugins

import org.gradle.api.Project
import org.jetbrains.dokka.gradle.DokkaExtension
import org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

val documentedDokkaModules: Map<String, String> = mapOf(
    ":domain:backup" to "backup",
    ":domain:calling" to "calling",
    ":sample:cli" to "cli",
    ":core:cryptography" to "cryptography",
    ":core:logger" to "logger",
    ":logic" to "logic",
    ":data:network" to "network",
    ":data:persistence" to "persistence",
    ":data:protobuf" to "protobuf"
)

/**
 * Adds a common Dokka configuration for the module, including:
 * - $MODULE_DIR$/module.md file
 * - Samples from the `samples` module
 * - Documentation of public and internal entities
 */
fun Project.commonDokkaConfig() {
    val publishedModule = documentedDokkaModules[path] ?: return

    plugins.apply("org.jetbrains.dokka")
    val kotlin = extensions.getByType(KotlinMultiplatformExtension::class.java)
    extensions.configure(DokkaExtension::class.java) {
        dokkaCacheDirectory.set(project.rootProject.layout.buildDirectory.dir(".cache/dokka"))
        // Keep the flat module URLs used by the existing published documentation.
        modulePath.set(publishedModule)
        dokkaPublications.configureEach {
            offlineMode.set(true)
        }
        dokkaSourceSets.configureEach {
            project.file("module.md").takeIf { it.exists() }?.let {
                includes.from(it)
            }
            includes.from(project.rootProject.file("dokka/moduledoc.md"))
            samples.from(project.rootProject.file("sample/samples/src/$name/kotlin"))
            documentedVisibilities.set(setOf(VisibilityModifier.Public, VisibilityModifier.Internal))
        }
    }
    // Apply overrides after Dokka has inferred the Kotlin source sets.
    afterEvaluate {
        configureDokkaCompatibility(extensions.getByType(DokkaExtension::class.java), kotlin)
    }
}
