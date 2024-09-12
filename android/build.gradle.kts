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

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    id(libs.plugins.android.application.get().pluginId)
    id(libs.plugins.kotlin.android.get().pluginId)
    alias(libs.plugins.compose.compiler)
}

android {
    setCompileSdkVersion(Android.Sdk.target)
    namespace = "com.wire.kalium.android"

    defaultConfig {
        applicationId = "com.wire.kalium.sample"
        targetSdk = Android.Sdk.target
        minSdk = Android.Sdk.min

        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = Android.testRunner
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }

    buildTypes {
        debug {}
        release {}
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += "-opt-in=kotlin.RequiresOptIn"
    }

    packaging {
        resources.pickFirsts.add("google/protobuf/*.proto")
        jniLibs.pickFirsts.add("**/libsodium.so")
    }
}

dependencies {
    implementation(project(":network"))
    implementation(project(":cryptography"))
    implementation(project(":logic"))
    implementation(libs.bundles.android)
    coreLibraryDesugaring(libs.desugarJdkLibs)
}
