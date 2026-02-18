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

val cliUseGlobalUserStorageCache: Boolean? = gradle.startParameter
    .projectProperties["USE_GLOBAL_USER_STORAGE_CACHE"]
    ?.toBooleanStrictOrNull()

val parentUseGlobalUserStorageCache: Boolean? = gradle.parent
    ?.rootProject
    ?.properties?.get("USE_GLOBAL_USER_STORAGE_CACHE")
    ?.toString()?.toBooleanStrictOrNull()

val localUseGlobalUserStorageCache: Boolean = providers
    .gradleProperty("USE_GLOBAL_USER_STORAGE_CACHE")
    .map { it.toBoolean() }
    .getOrElse(false)

val useGlobalUserStorageCache: Boolean = when {
    cliUseGlobalUserStorageCache != null -> cliUseGlobalUserStorageCache
    parentUseGlobalUserStorageCache != null -> parentUseGlobalUserStorageCache
    else -> localUseGlobalUserStorageCache
}
val generatedCommonMainKotlinDir = layout.buildDirectory.dir("generated/userstorage/commonMain/kotlin")

val generateUserStorageCacheConfig by tasks.registering {
    inputs.property("useGlobalUserStorageCache", useGlobalUserStorageCache)
    outputs.dir(generatedCommonMainKotlinDir)
    doLast {
        val packagePath = "com/wire/kalium/userstorage/di"
        val outDir = generatedCommonMainKotlinDir.get().asFile.resolve(packagePath)
        outDir.mkdirs()

        val outputFile = outDir.resolve("UserStorageBuildConfig.kt")
        outputFile.writeText(
            """
            package com.wire.kalium.userstorage.di

            internal const val USE_GLOBAL_USER_STORAGE_CACHE: Boolean = $useGlobalUserStorageCache
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
                implementation(projects.core.data)
                api(projects.data.persistence)
                implementation(libs.coroutines.core)
                implementation(libs.concurrentCollections)
                implementation(libs.statelyCommons)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(projects.core.util)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(projects.core.util)
            }
        }
        val appleMain by getting {
            dependencies {
                implementation(projects.core.util)
            }
        }
    }
}

tasks.matching { task ->
    task.name.contains("compile", ignoreCase = true) && task.name.contains("Kotlin")
}.configureEach {
    dependsOn(generateUserStorageCacheConfig)
}
