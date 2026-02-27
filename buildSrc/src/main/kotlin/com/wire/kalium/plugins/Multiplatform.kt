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

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion

/**
 * Applies the base configurations for a multiplatform module, including:
 * - Toggling of target platforms
 * - Basic Android settings, with SDK versions and Instrumentation Testing
 * - Dokka settings for documentation
 *
 * @see commonDokkaConfig
 */
@Suppress("LongParameterList")
internal fun Project.configureDefaultMultiplatform(
    enableApple: Boolean,
    enableJs: Boolean,
    enableJsTests: Boolean,
    includeNativeInterop: Boolean,
    enableIntegrationTests: Boolean,
    dependenciesToAdd: Set<FrequentModules>,
    androidNamespaceSuffix: String = this.name,
    jsModuleNameOverride: String? = null,
) {
    val kotlinExtension = extensions.findByType(KotlinMultiplatformExtension::class.java)
        ?: error("No multiplatform extension found. Is the Kotlin Multiplatform plugin applied to this module?")

    kotlinExtension.apply {
        compilerOptions {
            optIn.add("kotlin.RequiresOptIn")
            optIn.add("kotlin.uuid.ExperimentalUuidApi")
            freeCompilerArgs.add("-Xexpect-actual-classes")
        }

        jvmToolchain {
            languageVersion.set(JavaLanguageVersion.of(JavaVersion.VERSION_17.majorVersion))
        }

        applyDefaultHierarchyTemplate()

        jvm { commonJvmConfig(includeNativeInterop, enableIntegrationTests) }

        targets.withType(KotlinMultiplatformAndroidLibraryTarget::class.java).configureEach {
            commonAndroidLibConfig(this@configureDefaultMultiplatform, includeNativeInterop, androidNamespaceSuffix)
            // ⇣ DO NOT open a `dependencies {}` block here; add to the project instead:
            project.dependencies.add("coreLibraryDesugaring", library("desugarJdkLibs"))
        }

        if (enableJs) {
            js { commonJsConfig(jsModuleNameOverride, enableJsTests) }
        }
        if (enableApple) {
            commonAppleMultiplatformConfig()
        }
    }

    val androidDeviceTest = kotlinExtension.sourceSets.findByName("androidDeviceTest")
        ?: kotlinExtension.sourceSets.findByName("androidInstrumentedTest")
    androidDeviceTest?.dependencies {
        implementation(library("androidtest.core"))
        implementation(library("androidtest.runner"))
        implementation(library("androidtest.rules"))
    }

    val commonTest = kotlinExtension.sourceSets.getByName("commonTest")
    commonTest.dependencies {
        implementation(library("kotlin.test"))
    }

    val commonMain = kotlinExtension.sourceSets.getByName("commonMain")
    commonMain.dependencies {
        dependenciesToAdd.forEach {
            implementation(project(":${it.projectName}"))
        }
    }

    // Resolution strategy tweaks
    configurations.all {
        resolutionStrategy {
            force("org.jetbrains.kotlin:kotlin-test:${libs.findVersion("kotlin").get().requiredVersion}")
            eachDependency {
                if (requested.group == "net.java.dev.jna" && requested.name == "jna") {
                    useVersion("5.17.0")
                    because("Required for 16KB page support on Android 15+")
                }
                if (requested.group == "net.java.dev.jna" && requested.name == "jna-platform") {
                    useVersion("5.17.0")
                }
            }
        }
    }

    kotlinExtension.sourceSets.all {
        languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
    }

    commonDokkaConfig()
}
