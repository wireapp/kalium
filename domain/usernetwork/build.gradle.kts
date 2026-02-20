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

// Shared compile-time cache policy for provider-level caches across modules.
val providerCacheFlag = "USE_GLOBAL_PROVIDER_CACHE"

val useGlobalProviderCache: Boolean = resolveRequiredBooleanGradleProperty(
    propertyName = providerCacheFlag,
    purpose = "it defines provider cache scope (instance-local vs process-global)"
)

val generatedCommonMainKotlinDir = layout.buildDirectory.dir("generated/usernetwork/commonMain/kotlin")

val generateUserNetworkApiCacheConfig by tasks.registering {
    inputs.property("useGlobalProviderCache", useGlobalProviderCache)
    outputs.dir(generatedCommonMainKotlinDir)
    doLast {
        val packagePath = "com/wire/kalium/usernetwork/di"
        val outDir = generatedCommonMainKotlinDir.get().asFile.resolve(packagePath)
        outDir.mkdirs()

        val outputFile = outDir.resolve("UserNetworkBuildConfig.kt")
        outputFile.writeText(
            """
            package com.wire.kalium.usernetwork.di

            /**
             * Controls provider cache scope via shared compile-time policy.
             *
             * - `true`: all provider instances share one in-memory cache.
             * - `false`: each provider instance keeps an isolated in-memory cache.
             */
            internal const val USE_GLOBAL_PROVIDER_CACHE: Boolean = $useGlobalProviderCache
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
    task.name.contains("compile", ignoreCase = true)
}.configureEach {
    dependsOn(generateUserNetworkApiCacheConfig)
}
