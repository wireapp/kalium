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
    alias(libs.plugins.kotlin.serialization)
    id(libs.plugins.kalium.library.get().pluginId)
    alias(libs.plugins.ksp)
    alias(libs.plugins.mockative)
    alias(libs.plugins.mokkery)
}

kaliumLibrary {
    multiplatform {
        enableJs.set(false)
    }
}

kotlin {
    explicitApi()
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.core.common)
                api(projects.domain.work)
                implementation(projects.data.network)
                api(projects.core.data)
                implementation(projects.data.dataMappers)
                api(projects.data.networkUtil)
                implementation(projects.core.cryptography)
                implementation(projects.data.persistence)
                implementation(projects.data.protobuf)
                api(projects.core.logger)
                api(projects.domain.calling)
                implementation(projects.core.util)
                implementation(projects.domain.cells)
                implementation(projects.domain.backup)
                implementation(projects.domain.messaging.sending)

                // coroutines
                implementation(libs.coroutines.core)
                implementation(libs.ktxSerialization)
                implementation(libs.ktxDateTime)

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
                implementation(projects.core.common)
                implementation(projects.data.persistenceTest)
                implementation(projects.test.dataMocks)
                // coroutines
                implementation(libs.coroutines.test)
                implementation(libs.turbine)

                // mocking
                implementation(libs.okio.test)
                implementation(libs.settings.kmpTest)
            }
        }

        fun org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet.addCommonKotlinJvmSourceDir() {
            kotlin.srcDir("src/commonJvmAndroid/kotlin")
        }

        val appleMain by getting

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
