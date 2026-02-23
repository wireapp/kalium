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
val providerCacheScopeFlag = "kalium.providerCacheScope"
val providerCacheScopeValues = linkedSetOf("LOCAL", "GLOBAL")

val providerCacheScope: String = resolveRequiredEnumGradleProperty(
    propertyName = providerCacheScopeFlag,
    allowedValues = providerCacheScopeValues,
    purpose = "it defines provider cache scope (instance-local vs process-global)"
)
val generatedCommonMainKotlinDir = layout.buildDirectory.dir("generated/userstorage/commonMain/kotlin")

val generateUserStorageCacheConfig by tasks.registering {
    inputs.property("providerCacheScope", providerCacheScope)
    outputs.dir(generatedCommonMainKotlinDir)
    doLast {
        val packagePath = "com/wire/kalium/userstorage/di"
        val outDir = generatedCommonMainKotlinDir.get().asFile.resolve(packagePath)
        outDir.mkdirs()

        val outputFile = outDir.resolve("UserStorageBuildConfig.kt")
        outputFile.writeText(
            """
            package com.wire.kalium.userstorage.di

            /**
             * Controls provider cache scope via shared compile-time policy.
             *
             * - [ProviderCacheScope.GLOBAL]: all provider instances share one in-memory cache.
             * - [ProviderCacheScope.LOCAL]: each provider instance keeps an isolated in-memory cache.
             */
            internal enum class ProviderCacheScope { LOCAL, GLOBAL }
            internal val PROVIDER_CACHE_SCOPE: ProviderCacheScope = ProviderCacheScope.$providerCacheScope
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
    task.name.contains("compile", ignoreCase = true)
}.configureEach {
    dependsOn(generateUserStorageCacheConfig)
}
