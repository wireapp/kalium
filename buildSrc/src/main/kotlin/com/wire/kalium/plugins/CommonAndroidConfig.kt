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

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import org.gradle.api.Project

private const val BASE_NAMESPACE = "com.wire.kalium"

/**
 * @param includeNativeInterop if true, this android library
 * will have the Android NDK and cmake enabled.
 * @param namespaceSuffix the suffix added to [BASE_NAMESPACE]
 * that this Kalium library will use for generating R and BuildConfig classes.
 * Invalid characters like "-" are replaced with a dot (.).
 */
fun KotlinMultiplatformAndroidLibraryExtension.commonAndroidLibConfig(
    project: Project,
    includeNativeInterop: Boolean,
    namespaceSuffix: String
) {
    val sanitizedSuffix = namespaceSuffix.replace('-', '.')
    namespace = "$BASE_NAMESPACE.$sanitizedSuffix"
    compileSdk = Android.Sdk.compile
    minSdk = Android.Sdk.min
    lint.targetSdk = Android.Sdk.target
    enableCoreLibraryDesugaring = true

    packaging {
        resources.pickFirsts.add("google/protobuf/*.proto")
        jniLibs.pickFirsts.add("**/libsodium.so")
    }

    withDeviceTestBuilder {
        sourceSetTreeName = "test"
    }.configure {
        instrumentationRunner = Android.testRunner
    }
    withHostTestBuilder {
        sourceSetTreeName = "test"
    }

    if (project.file("consumer-proguard-rules.pro").exists()) {
        optimization.consumerKeepRules.file("consumer-proguard-rules.pro")
    }

    if (includeNativeInterop) {
        // Android-KMP does not expose the legacy externalNativeBuild DSL used with com.android.library.
        // Native interop in Kalium is configured through module-level dependencies/targets.
    }
}
