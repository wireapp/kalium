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
    multiplatform {
        enableApple.set(false)
        enableJs.set(false)
    }
}

kotlin {
    explicitApi()
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation {
        enabled.set(true)
    }

    sourceSets {
        val jvmMain by getting {
            dependencies {
                api(projects.logic.api)
                api(projects.data.eventsApi)
                api(projects.data.conversationApi)
                api(projects.data.network)
                api(projects.domain.eventProcessing)
                api(projects.domain.callingRuntime)
                implementation(projects.logic.runtime)
                implementation(projects.data.conversationRemote)
                implementation(projects.data.protobuf)
                implementation(projects.core.cryptography)
                implementation(projects.domain.calling)
                implementation(projects.domain.conversationRuntime)
                implementation(libs.coroutines.core)
                implementation(libs.jna)
                implementation(libs.ktxDateTime)
                implementation(libs.ktxSerialization)
            }
        }
    }
}

val serviceRuntimeClasspath = configurations.named("jvmRuntimeClasspath")

val verifyServiceModuleGraph = tasks.register("verifyServiceModuleGraph") {
    group = "verification"
    description = "Verifies that the headless service graph excludes client-only capabilities."

    doLast {
        val components = serviceRuntimeClasspath.get().incoming.resolutionResult.allComponents
        val projectPaths = components.mapNotNull { component ->
            (component.id as? org.gradle.api.artifacts.component.ProjectComponentIdentifier)?.projectPath
        }
        val forbiddenProjectPrefixes = setOf(
            ":data:persistence",
            ":domain:backup",
            ":domain:calling-history",
            ":domain:cells",
            ":domain:conversation-history",
            ":domain:messaging",
            ":domain:work"
        )
        val forbiddenProjects = projectPaths.filter { projectPath ->
            projectPath == ":logic" ||
                    projectPath == ":logic:client" ||
                    forbiddenProjectPrefixes.any { projectPath.startsWith(it) }
        }
        val forbiddenExternalModules = components.mapNotNull { component ->
            component.id as? org.gradle.api.artifacts.component.ModuleComponentIdentifier
        }.filter { module ->
            module.group.startsWith("androidx.paging") ||
                    module.group.startsWith("androidx.work") ||
                    module.module.contains("paging", ignoreCase = true)
        }.map { module -> "${module.group}:${module.module}" }

        check(forbiddenProjects.isEmpty()) {
            "Headless service graph contains client-only projects: ${forbiddenProjects.sorted()}"
        }
        check(forbiddenExternalModules.isEmpty()) {
            "Headless service graph contains client-only libraries: ${forbiddenExternalModules.sorted()}"
        }
    }
}

tasks.named("check") {
    dependsOn(verifyServiceModuleGraph)
}
