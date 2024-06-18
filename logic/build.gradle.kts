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
import org.jetbrains.kotlin.util.suffixIfNot

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    id(libs.plugins.android.library.get().pluginId)
    id(libs.plugins.kotlin.multiplatform.get().pluginId)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    id(libs.plugins.kalium.library.get().pluginId)
}

kaliumLibrary {
    multiplatform {
        enableJs.set(false)
    }
}

@Suppress("UnusedPrivateProperty")
kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":network"))
                api(project(":network-util"))
                implementation(project(":cryptography"))
                implementation(project(":persistence"))
                implementation(project(":protobuf"))
                api(project(":logger"))
                api(project(":calling"))
                implementation(project(":util"))

                // coroutines
                implementation(libs.coroutines.core)
                implementation(libs.ktxSerialization)
                implementation(libs.ktxDateTime)
                implementation(libs.benAsherUUID)

                // ktor mockk engine
                implementation(libs.ktor.mock)

                // the Dependency is duplicated between here and persistence build.gradle.kts
                implementation(libs.settings.kmp)

                // Okio
                implementation(libs.okio.core)

                implementation(libs.sqldelight.androidxPaging)
                // Concurrent collections
                implementation(libs.concurrentCollections)
                implementation(libs.statelyCommons)
                configurations.all {
                    exclude(group = "co.touchlab", module = "stately-strict-jvm")
                }
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(project(":persistence-test"))
                // coroutines
                implementation(libs.coroutines.test)
                implementation(libs.turbine)

                // mocking
                implementation(libs.mockative.runtime)
                implementation(libs.okio.test)
                implementation(libs.settings.kmpTest)
            }
        }

        fun org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet.addCommonKotlinJvmSourceDir() {
            kotlin.srcDir("src/commonJvmAndroid/kotlin")
        }

        val appleMain by getting {
            dependencies {
                implementation(libs.coreCrypto)
            }
        }

        val jvmMain by getting {
            addCommonKotlinJvmSourceDir()
            dependencies {
                implementation(libs.jna)
                implementation(libs.coreCryptoJvm)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(libs.konsist)
            }
        }
        val androidMain by getting {
            addCommonKotlinJvmSourceDir()
            dependencies {
                implementation(libs.work)
                implementation(libs.coreCryptoAndroid.get().let { "${it.module}:${it.versionConstraint.requiredVersion}" }) {
                    exclude("androidx.core")
                    exclude("androidx.appcompat")
                }
            }
        }
        val androidUnitTest by getting {
            dependencies {
                implementation(libs.robolectric)
                implementation(libs.core.ktx)
            }
        }
    }
}

android {
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    configurations
        .filter { it.name.startsWith("ksp") && it.name.contains("Test") }
        .forEach {
            add(it.name, libs.mockative.processor)
        }
}

android {
    testOptions.unitTests.all { test ->
        // only run tests that are different for the android platform, the rest is covered by the jvm tests
        file("src/androidUnitTest/kotlin").let { dir ->
            if (dir.exists() && dir.isDirectory) {
                dir.walk().forEach {
                    if (it.isFile && it.extension == "kt") {
                        it.relativeToOrNull(dir)?.let {
                            test.include(it.path.removeSuffix(".kt").suffixIfNot("*"))
                        }
                    }
                }
            }
        }
    }
}
