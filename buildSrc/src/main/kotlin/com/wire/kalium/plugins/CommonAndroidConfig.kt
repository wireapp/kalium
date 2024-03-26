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

import com.android.build.gradle.LibraryExtension
import org.gradle.api.JavaVersion
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget

private const val BASE_NAMESPACE = "com.wire.kalium"

fun KotlinAndroidTarget.commmonKotlinAndroidTargetConfig() {
    @OptIn(ExperimentalKotlinGradlePluginApi::class) // this is experimental API and will likely change in the future into more robust DSL
    instrumentedTestVariant {
        sourceSetTree.set(KotlinSourceSetTree.test)
    }
    /** NO-OP. Nothing to do here for now **/
}

/**
 * @param includeNativeInterop if true, this android library
 * will have the Android NDK and cmake enabled.
 * @param namespaceSuffix the suffix added to [BASE_NAMESPACE]
 * that this Kalium library will use for generating R and BuildConfig classes.
 * Invalid characters like "-" are replaced with a dot (.).
 */
fun LibraryExtension.commonAndroidLibConfig(
    includeNativeInterop: Boolean,
    namespaceSuffix: String
) {
    val sanitizedSuffix = namespaceSuffix.replace('-', '.')
    namespace = "$BASE_NAMESPACE.$sanitizedSuffix"
    compileSdk = Android.Sdk.compile
    sourceSets.getByName("main").manifest.srcFile("src/androidMain/AndroidManifest.xml")
    defaultConfig {
        minSdk = Android.Sdk.min
        targetSdk = Android.Sdk.target
        consumerProguardFiles("consumer-proguard-rules.pro")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    packaging {
        resources.pickFirsts.add("google/protobuf/*.proto")
        jniLibs.pickFirsts.add("**/libsodium.so")
    }

    // No Android Unit test. JVM does that. Android runs on emulator
    sourceSets.remove(sourceSets.getByName("test"))

    if (includeNativeInterop) {
        externalNativeBuild {
            cmake {
                version = Android.Ndk.cMakeVersion
            }
            ndkBuild {
                ndkVersion = Android.Ndk.version
                // path(File("src/androidMain/jni/Android.mk"))
            }
        }
    }
}
