/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

plugins {
    id(libs.plugins.kalium.library.get().pluginId)
}

kaliumLibrary {
    multiplatform { enableJs.set(false) }
}

kotlin {
    explicitApi()
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation {
        enabled.set(true)
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.logic)
                api(projects.domain.usernetwork)
            }
        }
        val appleMain by getting {
            dependencies {
                api(projects.data.persistence)
            }
        }
    }
}

val clientRuntimeClasspath = configurations.named("jvmRuntimeClasspath")
val clientAndroidRuntimeClasspath = configurations.named("androidRuntimeClasspath")

val verifyClientModuleGraph = tasks.register("verifyClientModuleGraph") {
    group = "verification"
    description = "Verifies that the full client composition retains its legacy capability graph."

    doLast {
        fun projectPaths(configuration: org.gradle.api.artifacts.Configuration): Set<String> =
            configuration.incoming.resolutionResult.allComponents.mapNotNull { component ->
                (component.id as? org.gradle.api.artifacts.component.ProjectComponentIdentifier)?.projectPath
            }.toSet()

        val requiredProjects = setOf(
            ":logic",
            ":data:persistence",
            ":domain:backup",
            ":domain:messaging:sending",
            ":domain:work"
        )
        val clientAppleMetadataClasspath = configurations.getByName("appleMainResolvableDependenciesMetadata")
        val missingJvmProjects = requiredProjects - projectPaths(clientRuntimeClasspath.get())
        val missingAndroidProjects = requiredProjects - projectPaths(clientAndroidRuntimeClasspath.get())
        val missingAppleProjects = setOf(":logic", ":data:persistence") - projectPaths(clientAppleMetadataClasspath)
        val androidModules = clientAndroidRuntimeClasspath.get().incoming.resolutionResult.allComponents.mapNotNull { component ->
            component.id as? org.gradle.api.artifacts.component.ModuleComponentIdentifier
        }

        check(missingJvmProjects.isEmpty()) {
            "Full JVM client graph is missing legacy capabilities: ${missingJvmProjects.sorted()}"
        }
        check(missingAndroidProjects.isEmpty()) {
            "Full Android client graph is missing legacy capabilities: ${missingAndroidProjects.sorted()}"
        }
        check(missingAppleProjects.isEmpty()) {
            "Full Apple client graph is missing legacy capabilities: ${missingAppleProjects.sorted()}"
        }
        check(androidModules.any { it.group == "androidx.work" }) {
            "Full Android client graph is missing Android WorkManager"
        }
    }
}

val androidClientClass = "com.wire.kalium.logic.client.KaliumClient"
val androidClientClasses = layout.buildDirectory.dir("classes/kotlin/android/main")
val androidClientAbi = layout.projectDirectory.file("api/android/client.api")

val verifyClientAndroidAbi = tasks.register("verifyClientAndroidAbi") {
    group = "verification"
    description = "Checks the Android KaliumClient binary signature omitted by the built-in KMP ABI task."
    dependsOn("compileAndroidMain")
    inputs.dir(androidClientClasses)
    inputs.file(androidClientAbi)

    doLast {
        val javap = file("${System.getProperty("java.home")}/bin/javap")
        check(javap.isFile) { "JDK javap is required for Android ABI validation" }
        val actual = providers.exec {
            commandLine(
                javap.absolutePath,
                "-classpath",
                androidClientClasses.get().asFile.absolutePath,
                "-public",
                "-s",
                androidClientClass,
            )
        }.standardOutput.asText.get().replace("\r\n", "\n").trimEnd() + "\n"
        val expected = androidClientAbi.asFile.readText().replace("\r\n", "\n")

        check(actual == expected) {
            "Android KaliumClient ABI changed. Review and update ${androidClientAbi.asFile} if intentional."
        }
    }
}

tasks.named("check") {
    dependsOn(verifyClientModuleGraph, verifyClientAndroidAbi)
}

tasks.named("checkKotlinAbi") {
    dependsOn(verifyClientAndroidAbi)
}
