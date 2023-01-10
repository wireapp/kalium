package com.wire.kalium.plugins

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Applies the base configurations for a multiplatform module, including:
 * - Toggling of target platforms
 * - Basic Android settings, with SDK versions and Instrumentation Testing
 * - Dokka settings for documentation
 *
 * @see commonDokkaConfig
 */
fun Project.configureDefaultMultiplatform(
    enableDarwin: Boolean,
    enableJs: Boolean,
    enableJsTests: Boolean,
    includeNativeInterop: Boolean
) {
    val kotlinExtension = extensions.findByName("kotlin") as? KotlinMultiplatformExtension
    check(kotlinExtension != null) {
        "No multiplatform extension found. Is the Kotlin Multiplatform plugin applied to this module?"
    }
    kotlinExtension.apply {
        jvm { commonJvmConfig(includeNativeInterop) }

        android { commmonKotlinAndroidTargetConfig() }

        if (enableJs) {
            js { commonJsConfig(enableJsTests) }
        }

        if (enableDarwin) {
            commonDarwinMultiplatformConfig()
        }
    }

    (this as org.gradle.api.plugins.ExtensionAware).extensions
        .configure<com.android.build.gradle.LibraryExtension>("android") {
            commonAndroidLibConfig(includeNativeInterop)
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
