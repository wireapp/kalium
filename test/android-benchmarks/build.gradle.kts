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

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    alias(libs.plugins.androidx.benchmark)
}

android {
    namespace = "com.wire.kalium.benchmarks.android"
    compileSdk = Android.Sdk.compile

    defaultConfig {
        minSdk = Android.Sdk.min
        testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"
    }

    // androidx.benchmark refuses to produce numbers from debuggable builds. Run the instrumented
    // tests against the release variant: `./gradlew :test:android-benchmarks:connectedReleaseAndroidTest`.
    testBuildType = "release"

    buildTypes {
        debug {
            // Keep debug available so the module still builds under the default variant.
        }
        release {
            isMinifyEnabled = false
            isDefault = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        isCoreLibraryDesugaringEnabled = true
    }

    packaging {
        // :data:persistence pulls in libsodium via kalium's crypto graph; match the rule the rest
        // of the project uses to avoid duplicate .so conflicts.
        jniLibs.pickFirsts.add("**/libsodium.so")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>()
    .matching { it.name.contains("AndroidTest", ignoreCase = true) }
    .configureEach {
        compilerOptions.optIn.add("com.wire.kalium.util.DebugKaliumApi")
    }

dependencies {
    coreLibraryDesugaring(libs.desugarJdkLibs)
    androidTestImplementation(projects.data.persistence)
    androidTestImplementation(libs.androidx.benchmark.junit4)
    androidTestImplementation(libs.androidtest.runner)
    androidTestImplementation(libs.androidtest.core)
    androidTestImplementation(libs.androidtest.ext.junit)
    androidTestImplementation(libs.coroutines.test)
    androidTestImplementation(libs.ktxDateTime)
}

// Override the SQLCipher version used transitively by :data:persistence. Useful when comparing
// benchmark runs across SQLCipher versions without editing the shared version catalog.
//     ./gradlew :test:android-benchmarks:connectedReleaseAndroidTest -PsqlcipherVersion=4.14.1
val sqlcipherOverride = project.findProperty("sqlcipherVersion") as String?
if (sqlcipherOverride != null) {
    logger.lifecycle("[android-benchmarks] Forcing net.zetetic:sqlcipher-android:$sqlcipherOverride")
    configurations.configureEach {
        resolutionStrategy.force("net.zetetic:sqlcipher-android:$sqlcipherOverride")
    }
}
