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
}

kaliumLibrary {
    multiplatform { enableJs.set(true) }
}

@Suppress("UnusedPrivateProperty")
kotlin {
    val xcf = XCFramework()
    val appleTargets = listOf(iosX64(), iosArm64(), iosSimulatorArm64(), macosArm64(), macosX64())
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
        val iosX64Main by getting {
            dependencies {
                implementation(libs.pbandk.runtime.iosX64)
            }
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
    }
}
