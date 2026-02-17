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

fun Map<String, String>.findPropertyIgnoringCase(name: String): String? =
    entries.firstOrNull { (propertyName, _) -> propertyName.equals(name, ignoreCase = true) }?.value

fun readBooleanPropertyFromProjectGradleProperties(propertyName: String, defaultValue: Boolean): Boolean {
    val gradlePropertiesFile = rootProject.layout.projectDirectory.file("gradle.properties").asFile
    if (!gradlePropertiesFile.exists()) {
        return defaultValue
    }

    val propertyValue = gradlePropertiesFile.useLines { lines ->
        lines
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line ->
                val separatorIndex = line.indexOf('=')
                if (separatorIndex < 0) {
                    null
                } else {
                    line.substring(0, separatorIndex).trim() to line.substring(separatorIndex + 1).trim()
                }
            }
            .firstOrNull { (name, _) -> name == propertyName }
            ?.second
    }

    return propertyValue?.toBooleanStrictOrNull() ?: defaultValue
}

val cliUseGlobalUserNetworkApiCacheRaw: String? = gradle.startParameter
    .projectProperties.findPropertyIgnoringCase("USE_GLOBAL_USER_NETWORK_API_CACHE")
    ?: gradle.parent?.startParameter?.projectProperties?.findPropertyIgnoringCase("USE_GLOBAL_USER_NETWORK_API_CACHE")

val cliUseGlobalUserNetworkApiCache: Boolean? = cliUseGlobalUserNetworkApiCacheRaw?.toBooleanStrictOrNull()

val useGlobalUserNetworkApiCache: Boolean = when {
    cliUseGlobalUserNetworkApiCache != null -> cliUseGlobalUserNetworkApiCache
    gradle.parent != null -> true
    else -> readBooleanPropertyFromProjectGradleProperties(
        propertyName = "USE_GLOBAL_USER_NETWORK_API_CACHE",
        defaultValue = false
    )
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
