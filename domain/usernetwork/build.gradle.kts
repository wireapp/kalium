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

val cliUseGlobalUserNetworkApiCache: Boolean? = gradle.startParameter
    .projectProperties["USE_GLOBAL_USER_NETWORK_API_CACHE"]
    ?.toBooleanStrictOrNull()

val parentUseGlobalUserNetworkApiCache: Boolean? = gradle.parent
    ?.rootProject
    ?.properties?.get("USE_GLOBAL_USER_NETWORK_API_CACHE")
    ?.toString()?.toBooleanStrictOrNull()

val localUseGlobalUserNetworkApiCache: Boolean = providers
    .gradleProperty("USE_GLOBAL_USER_NETWORK_API_CACHE")
    .map { it.toBoolean() }
    .getOrElse(false)

val useGlobalUserNetworkApiCache: Boolean = when {
    cliUseGlobalUserNetworkApiCache != null -> cliUseGlobalUserNetworkApiCache
    parentUseGlobalUserNetworkApiCache != null -> parentUseGlobalUserNetworkApiCache
    else -> localUseGlobalUserNetworkApiCache
}

val generatedCommonMainKotlinDir = layout.buildDirectory.dir("generated/usernetwork/commonMain/kotlin")

val generateUserNetworkApiCacheConfig by tasks.registering {
    inputs.property("useGlobalUserNetworkApiCache", useGlobalUserNetworkApiCache)
    outputs.dir(generatedCommonMainKotlinDir)
    doLast {
        val packagePath = "com/wire/kalium/usernetwork/di"
        val outDir = generatedCommonMainKotlinDir.get().asFile.resolve(packagePath)
        outDir.mkdirs()

        val outputFile = outDir.resolve("UserNetworkBuildConfig.kt")
        outputFile.writeText(
            """
            package com.wire.kalium.usernetwork.di

            internal const val USE_GLOBAL_USER_NETWORK_API_CACHE: Boolean = $useGlobalUserNetworkApiCache
            """.trimIndent() + "\n"
        )
    }
}

kaliumLibrary {
    multiplatform {
        enableJs.set(false)
    }
}

kotlin {
    explicitApi()
    sourceSets {
        val commonMain by getting {
            kotlin.srcDir(generatedCommonMainKotlinDir)
            dependencies {
                api(projects.data.network)
                implementation(libs.concurrentCollections)
                implementation(libs.statelyCommons)
            }
        }
    }
}

tasks.matching { task ->
    task.name.contains("compile", ignoreCase = true) && task.name.contains("Kotlin")
}.configureEach {
    dependsOn(generateUserNetworkApiCacheConfig)
}
