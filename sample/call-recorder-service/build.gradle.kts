/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

import com.wire.kalium.plugins.commonJvmConfig

plugins {
    kotlin("multiplatform")
}

val mainFunctionClassName = "com.wire.kalium.sample.callrecorder.MainKt"

kotlin {
    jvm {
        commonJvmConfig(includeNativeInterop = false)
        mainRun {
            mainClass.set(mainFunctionClassName)
        }
    }

    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(projects.logic.service)
                implementation(projects.core.cryptography)
                implementation(libs.coroutines.core)
            }
        }
    }
}

tasks.withType<JavaExec>().configureEach {
    jvmArgs("-Djava.library.path=./native/libs")
}

val recorderRuntimeClasspath = configurations.named("jvmRuntimeClasspath")

val verifyRecorderModuleGraph = tasks.register("verifyRecorderModuleGraph") {
    group = "verification"
    description = "Verifies that the call recorder excludes client-only capabilities."

    doLast {
        val projectPaths = recorderRuntimeClasspath.get().incoming.resolutionResult.allComponents.mapNotNull { component ->
            (component.id as? org.gradle.api.artifacts.component.ProjectComponentIdentifier)?.projectPath
        }
        val forbiddenPrefixes = setOf(
            ":data:persistence",
            ":domain:backup",
            ":domain:calling-history",
            ":domain:cells",
            ":domain:conversation-history",
            ":domain:messaging",
            ":domain:work",
        )
        val forbidden = projectPaths.filter { path ->
            path == ":logic" || path == ":logic:client" || forbiddenPrefixes.any(path::startsWith)
        }
        check(forbidden.isEmpty()) { "Call recorder contains client-only projects: ${forbidden.sorted()}" }
    }
}

tasks.named("check") {
    dependsOn(verifyRecorderModuleGraph)
}
