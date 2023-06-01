/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Applies the base configurations for a multiplatform module, including:
 * - Toggling of target platforms
 * - Basic Android settings, with SDK versions and Instrumentation Testing
 * - Dokka settings for documentation
 *
 * @see commonDokkaConfig
 */
@OptIn(ExperimentalKotlinGradlePluginApi::class)
fun Project.configureDefaultMultiplatform(
    enableApple: Boolean,
    enableJs: Boolean,
    enableJsTests: Boolean,
    includeNativeInterop: Boolean,
    androidNamespaceSuffix: String = this.name
) {
    val kotlinExtension = extensions.findByName("kotlin") as? KotlinMultiplatformExtension
    check(kotlinExtension != null) {
        "No multiplatform extension found. Is the Kotlin Multiplatform plugin applied to this module?"
    }
    kotlinExtension.apply {
        targetHierarchy.default()
        jvm { commonJvmConfig(includeNativeInterop) }

        android { commmonKotlinAndroidTargetConfig() }

        if (enableJs) {
            js { commonJsConfig(enableJsTests) }
        }

        if (enableApple) {
            commonAppleMultiplatformConfig()
        }
    }

    (this as org.gradle.api.plugins.ExtensionAware).extensions
        .configure<com.android.build.gradle.LibraryExtension>("android") {
            commonAndroidLibConfig(includeNativeInterop, androidNamespaceSuffix)
        }

    // Add common runner and rules to Android Instrumented Tests
    kotlinExtension.sourceSets.getByName("androidAndroidTest") {
        dependencies {
            implementation(library("androidtest.core"))
            implementation(library("androidtest.runner"))
            implementation(library("androidtest.rules"))
        }
    }

    commonDokkaConfig()
}
