import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

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
plugins {
    id(libs.plugins.android.library.get().pluginId)
    id(libs.plugins.kotlin.multiplatform.get().pluginId)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    id(libs.plugins.kalium.library.get().pluginId)
    alias(libs.plugins.kotlinNativeCoroutines)
}

kaliumLibrary {
    multiplatform { enableJs.set(true) }
}

android {
    // Because of native libraries, we can only test Android code on instrumentation tests
    testOptions.unitTests.all {
        it.enabled = false
    }
}

kotlin {
    // Makes visibility modifiers mandatory
    // Useful for a library that will be called by other clients
    // This way we need to think before putting "public" in things, and we can be reminded by the compiler to use "internal" more often
    explicitApi()
    val xcf = XCFramework()
    val appleTargets = listOf(iosArm64(), iosSimulatorArm64(), macosArm64(), macosX64())
    appleTargets.forEach {
        it.binaries.framework {
            baseName = "backup"
            xcf.add(this)
        }
    }
    js {
        browser()
        binaries.library()
        generateTypeScriptDefinitions()
    }
    sourceSets {
        all {
            languageSettings.optIn("kotlin.ExperimentalUnsignedTypes")
            languageSettings.optIn("kotlin.experimental.ExperimentalObjCRefinement")
            languageSettings.optIn("kotlin.experimental.ExperimentalObjCName")
            languageSettings.optIn("kotlin.js.ExperimentalJsExport")
        }
        val commonMain by getting {
            dependencies {
                implementation(project(":data"))
                implementation(project(":protobuf"))
                implementation(libs.pbandk.runtime.common)

                implementation(libs.coroutines.core)
                implementation(libs.ktxDateTime)
                implementation(libs.ktxSerialization)

                implementation(libs.okio.core)

                // Libsodium
                implementation(libs.libsodiumBindingsMP)
                api(libs.kermit)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.coroutines.test)
                implementation(libs.okio.test)
            }
        }

        val nonJsMain by creating {
            dependsOn(commonMain)
        }
        val nonJsTest by creating {
            dependsOn(commonTest)
        }
        val androidMain by getting {
            dependsOn(nonJsMain)
        }
        val androidInstrumentedTest by getting {
            dependsOn(nonJsTest)
        }
        val androidUnitTest by getting {
            // Although UNIT tests for Android are disabled (only run Instrumented), we need to add this in order to resolve
            // expect/actual definitions during test compilation phase.
            dependsOn(nonJsTest)
        }
        val jvmMain by getting {
            dependsOn(nonJsMain)
        }
        val jvmTest by getting {
            dependsOn(nonJsTest)
        }
        val appleMain by getting {
            dependsOn(nonJsMain)
        }
        val appleTest by getting {
            dependsOn(nonJsTest)
        }
        val iosArm64Main by getting {
            dependencies {
                implementation(libs.pbandk.runtime.iosArm64)
            }
        }
        val iosSimulatorArm64Main by getting {
            dependencies {
                implementation(libs.pbandk.runtime.iosSimulatorArm64)
            }
        }
        val macosX64Main by getting {
            dependencies {
                implementation(libs.pbandk.runtime.macX64)
            }
        }
        val macosArm64Main by getting {
            dependencies {
                implementation(libs.pbandk.runtime.macArm64)
            }
        }
        val jsMain by getting {
            dependencies {
                implementation(npm("jszip", "3.10.1"))
            }
        }
    }
}
