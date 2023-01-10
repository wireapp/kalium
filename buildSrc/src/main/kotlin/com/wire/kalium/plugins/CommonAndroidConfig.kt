package com.wire.kalium.plugins

import com.android.build.gradle.LibraryExtension
import org.gradle.api.JavaVersion
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget

fun KotlinAndroidTarget.commmonKotlinAndroidTargetConfig() {
    /** NO-OP. Nothing to do here for now **/
}

fun LibraryExtension.commonAndroidLibConfig(includeNativeInterop: Boolean) {
    compileSdk = Android.Sdk.compile
    sourceSets.getByName("main").manifest.srcFile("src/androidMain/AndroidManifest.xml")
    defaultConfig {
        minSdk = Android.Sdk.min
        targetSdk = Android.Sdk.target
        consumerProguardFiles("consumer-proguard-rules.pro")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    packagingOptions {
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
