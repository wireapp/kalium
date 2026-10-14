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
import org.jetbrains.dokka.gradle.engine.parameters.KotlinPlatform
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
    // Apply these overrides after Dokka has inferred the Kotlin source sets.
    afterEvaluate {
        extensions.configure(DokkaExtension::class.java) {
            val inferredSourceSets = dokkaSourceSets.toList()
            val inferredSourceRoots = inferredSourceSets.associateWith { it.sourceRoots.from.toList() }
            val buildDirectory = project.layout.buildDirectory.get().asFile.toPath()
            val kotlinSourceSets = inferredSourceSets.mapNotNull { sourceSet ->
                kotlin.sourceSets.findByName(sourceSet.name)?.let { sourceSet to it }
            }.toMap()
            // Intermediate metadata can require compiling SDK source sets whose
            // platform bindings are declared only on leaf targets. Analyze those
            // documentation sets with a real leaf target's dependency artifacts.
            val jvmClasspath = dokkaSourceSets.findByName("jvmMain")?.classpath?.from?.toList()
            val nativeClasspath = dokkaSourceSets.findByName("macosArm64Main")?.classpath?.from?.toList()
            val iosClasspath = dokkaSourceSets.findByName("iosArm64Main")?.classpath?.from?.toList()
            inferredSourceSets.filter { project.name in setOf("cryptography", "logic", "cli") }.forEach { sourceSet ->
                when (sourceSet.analysisPlatform.get()) {
                    KotlinPlatform.Common -> if (project.name != "cryptography" || sourceSet.name != "commonMain") {
                        jvmClasspath?.let {
                            sourceSet.classpath.setFrom(it)
                            sourceSet.analysisPlatform.set(KotlinPlatform.JVM)
                        }
                    }
                    KotlinPlatform.Native -> if (sourceSet.name !in setOf("iosArm64Main", "iosSimulatorArm64Main", "macosArm64Main")) {
                        val targetClasspath = if (sourceSet.name.startsWith("ios")) iosClasspath else nativeClasspath
                        targetClasspath?.let { sourceSet.classpath.setFrom(it) }
                    }
                    else -> Unit
                }
            }
            val sharedRoots = kotlinSourceSets.entries
                .flatMap { (dokkaSourceSet, kotlinSourceSet) ->
                    kotlinSourceSet.kotlin.srcDirs
                        .filter { !it.toPath().startsWith(buildDirectory) }
                        .map { it to dokkaSourceSet }
                }
                .groupBy({ it.first }, { it.second })
                .filterValues { it.size > 1 }

            inferredSourceSets.forEach { sourceSet ->
                // Include generated declarations for type resolution. Dokka's
                // suppressGeneratedFiles keeps them out of the published API pages.
                // Use files so Kotlin excludes (such as appleMain's call code) are respected.
                sourceSet.sourceRoots.setFrom(project.provider {
                    val sourceFiles = kotlinSourceSets[sourceSet]?.kotlin?.files
                        ?: project.files(inferredSourceRoots.getValue(sourceSet)).asFileTree.files
                    sourceFiles.filter { sourceFile ->
                        sharedRoots.keys.none { sourceFile.toPath().startsWith(it.toPath()) }
                    }
                })
            }

            // Some modules share directories using srcDir rather than dependsOn. K2
            // requires each file to belong to one source set, so represent these shared
            // directories once in Dokka and let their original platforms depend on them.
            sharedRoots.entries.groupBy({ it.value.toSet() }, { it.key }).forEach { (owners, roots) ->
                val sharedName = "shared" + owners.map { it.name }.sorted().joinToString("")
                val shared = dokkaSourceSets.maybeCreate(sharedName)
                shared.displayName.set(owners.map { it.displayName.get() }.sorted().joinToString(" / "))
                val platforms = owners.map { it.analysisPlatform.get() }.toSet()
                shared.analysisPlatform.set(
                    if (KotlinPlatform.JVM in platforms) {
                        KotlinPlatform.JVM
                    } else {
                        platforms.singleOrNull() ?: KotlinPlatform.Common
                    }
                )
                // Use a single compatible platform's dependency graph for shared code.
                val analysisOwner = owners.firstOrNull { it.analysisPlatform.get() == shared.analysisPlatform.get() }
                    ?: owners.first()
                shared.classpath.from(analysisOwner.classpath)
                shared.sourceRoots.setFrom(project.provider {
                    owners.flatMap { kotlinSourceSets.getValue(it).kotlin.files }.filter { sourceFile ->
                        roots.any { sourceFile.toPath().startsWith(it.toPath()) }
                    }.toSet()
                })
                fun ancestors(name: String): Set<String> = kotlin.sourceSets.getByName(name).dependsOn
                    .flatMap { setOf(it.name) + ancestors(it.name) }.toSet()
                val commonAncestors = owners.map { ancestors(it.name) }.reduce { left, right -> left intersect right }
                commonAncestors.forEach { name ->
                    shared.dependentSourceSets.add(dokkaSourceSets.getByName(name).sourceSetId.get())
                }
                owners.forEach { it.dependentSourceSets.add(shared.sourceSetId.get()) }
            }
        }
    }
}
