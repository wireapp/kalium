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

import com.wire.kalium.plugins.appleTargets

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    id(libs.plugins.android.library.get().pluginId)
    id(libs.plugins.kotlin.multiplatform.get().pluginId)
    alias(libs.plugins.kotlin.serialization)
    id(libs.plugins.kalium.library.get().pluginId)
    alias(libs.plugins.ksp)
    alias(libs.plugins.mockative)
}

kaliumLibrary {
    multiplatform {
        includeNativeInterop.set(true)
    }
}

kotlin {
    iosX64 {
        binaries.all {
            linkerOpts("-framework", "Security")
        }
    }
    iosArm64 {
        binaries.all {
            linkerOpts("-framework", "Security")
        }
    }
    iosSimulatorArm64 {
        binaries.all {
            linkerOpts("-framework", "Security")
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":logger"))
                // coroutines
                implementation(libs.coroutines.core)
                api(libs.ktor.core)

                // KTX
                implementation(libs.ktxDateTime)

                // Okio
                implementation(libs.okio.core)

                // Libsodium
                implementation(libs.libsodiumBindingsMP)

                // mocking
                implementation(libs.mockative.runtime)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.coroutines.test)
                implementation(libs.okio.test)
            }
        }

        fun org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet.addCommonKotlinJvmSourceDir() {
            kotlin.srcDir("src/commonJvmAndroid/kotlin")
        }

        val jvmMain by getting {
            addCommonKotlinJvmSourceDir()
            dependencies {
                implementation(libs.cryptobox4j)
                implementation(libs.coreCryptoJvm)
            }
        }
        val jvmTest by getting
        val jsMain by getting {
            dependencies {
                implementation(npm("@wireapp/cryptobox", "12.7.2", generateExternals = false))
                implementation(npm("@wireapp/store-engine", "4.9.9", generateExternals = false))
            }
        }
        val jsTest by getting
        val androidMain by getting {
            addCommonKotlinJvmSourceDir()
            dependencies {
                implementation(libs.cryptoboxAndroid)
                implementation(libs.androidCrypto)
                implementation(libs.coreCryptoAndroid.get().let { "${it.module}:${it.versionConstraint.requiredVersion}" }) {
                    exclude("androidx.core")
                    exclude("androidx.appcompat")
                }
            }
        }
        val appleMain by getting {
            dependencies {
                implementation(libs.coreCrypto)
            }
        }
    }
}

project.appleTargets().forEach {
    registerCopyTestResourcesTask(it)
}

android {
    testOptions.unitTests.all {
        it.enabled = false
    }
}
