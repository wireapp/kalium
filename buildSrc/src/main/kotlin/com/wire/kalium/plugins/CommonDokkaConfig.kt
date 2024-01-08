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

private val DOKKA_CACHE_DIR = ".cache/dokka"

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
    rootProject.mkdir("build/$DOKKA_CACHE_DIR") // creating cache dir

    tasks.withType(AbstractDokkaLeafTask::class.java).configureEach {
        cacheRoot.set(rootProject.buildDir.resolve(DOKKA_CACHE_DIR))  // set cache config dir to rootProject/buildDir
        offlineMode.set(true) // offline, since we don't do online package-list

        dokkaSourceSets.configureEach {
            file("module.md").takeIf { it.exists() }?.let {
                includes.from(it)
            }
            includes.from(rootProject.file("dokka/moduledoc.md").path)
            samples.from(rootProject.file("samples"))
            includeNonPublic.set(true)
            documentedVisibilities.set(listOf(Visibility.PUBLIC, Visibility.INTERNAL))
            classpath.setFrom("protobuf") // not sure... but why not?
        }
    }
}

