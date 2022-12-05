package com.wire.kalium.plugins

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

fun Project.configureDefaultMultiplatform(
    enableiOS: Boolean,
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

        if (enableiOS) {
            // TODO: check arch of current system (X64 or ARM64) and enable accordingly?
            //       devs on Apple Silicon should be able to run ARM tests
            //       devs on Intel Macbooks should be able to run X64 tests
            //       this should require us moving iOS code from iOSX64Main to
            //       another sourceSet (iOSMain/iOSCommon or similar)
            iosX64()
        }
    }

    (this as org.gradle.api.plugins.ExtensionAware).extensions
        .configure<com.android.build.gradle.LibraryExtension>("android") {
            commonAndroidLibConfig(includeNativeInterop)
        }
}
