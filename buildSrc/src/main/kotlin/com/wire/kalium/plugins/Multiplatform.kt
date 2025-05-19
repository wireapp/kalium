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
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Applies the base configurations for a multiplatform module, including:
 * - Toggling of target platforms
 * - Basic Android settings, with SDK versions and Instrumentation Testing
 * - Dokka settings for documentation
 *
 * @see commonDokkaConfig
 */
@Suppress("LongParameterList")
fun Project.configureDefaultMultiplatform(
    enableApple: Boolean,
    enableJs: Boolean,
    enableJsTests: Boolean,
    includeNativeInterop: Boolean,
    enableIntegrationTests: Boolean,
    androidNamespaceSuffix: String = this.name,
    jsModuleNameOverride: String? = null,
) {
    val kotlinExtension = extensions.findByName("kotlin") as? KotlinMultiplatformExtension
    check(kotlinExtension != null) {
        "No multiplatform extension found. Is the Kotlin Multiplatform plugin applied to this module?"
    }
    kotlinExtension.apply {
        applyDefaultHierarchyTemplate()
        jvm { commonJvmConfig(includeNativeInterop, enableIntegrationTests) }

        androidTarget {
            commmonKotlinAndroidTargetConfig()
            dependencies {
                add("coreLibraryDesugaring", library("desugarJdkLibs"))
            }
        }

        if (enableJs) {
            js { commonJsConfig(jsModuleNameOverride, enableJsTests) }
        }

        if (enableApple) {
            commonAppleMultiplatformConfig()
        }
    }

    (this as org.gradle.api.plugins.ExtensionAware).extensions
        .configure<com.android.build.gradle.LibraryExtension>("android") {
            commonAndroidLibConfig(includeNativeInterop, androidNamespaceSuffix)
        }

    kotlinExtension.sourceSets.getByName("androidInstrumentedTest") {

        dependencies {
            // Add common runner and rules to Android Instrumented Tests
            implementation(library("androidtest.core"))
            implementation(library("androidtest.runner"))
            implementation(library("androidtest.rules"))
        }
    }

    kotlinExtension.sourceSets.getByName("commonTest") {
        dependencies {
            implementation(library("kotlin.test"))
        }
    }

    configurations.all {
        resolutionStrategy {
            force("org.jetbrains.kotlin:kotlin-test:${libs.findVersion("kotlin").get().requiredVersion}")
        }
    }

    kotlinExtension.sourceSets.all {
        languageSettings {
            // Most of the native code requires this opt-in
            // We absolutely need it and are accepting the risk of the API being experimental
            optIn("kotlinx.cinterop.ExperimentalForeignApi")
        }
    }

    commonDokkaConfig()
}
