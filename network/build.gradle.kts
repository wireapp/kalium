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
    id(libs.plugins.android.library.get().pluginId)
    id(libs.plugins.kotlin.multiplatform.get().pluginId)
    alias(libs.plugins.kotlin.serialization)
    id(libs.plugins.kalium.library.get().pluginId)
    alias(libs.plugins.ksp)
    alias(libs.plugins.mockative)
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
                implementation(project(":protobuf"))
                implementation(project(":util"))
                implementation(project(":network-util"))
                api(project(":network-model"))
                api(project(":logger"))

                // coroutines
                implementation(libs.coroutines.core)

                // ktor
                api(libs.ktor.core)
                implementation(libs.ktor.utils)
                implementation(libs.ktor.json)
                implementation(libs.ktor.serialization)
                implementation(libs.ktor.logging)
                implementation(libs.ktor.authClient)
                implementation(libs.ktor.webSocket)
                implementation(libs.ktor.contentNegotiation)
                implementation(libs.ktor.encoding)

                // mock engine
                implementation(libs.ktor.mock)

                // KTX
                implementation(libs.ktxDateTime)
                implementation(libs.ktx.atomicfu)

                // Okio
                implementation(libs.okio.core)
                implementation(libs.okio.test)

                // UUIDs
                implementation(libs.benAsherUUID)

                // mocking
                implementation(libs.mockative.runtime)
            }
        }
        val commonTest by getting {
            dependencies {
                // coroutines
                implementation(libs.coroutines.test)
                // ktor test
                implementation(libs.ktor.mock)
                // mocks
                implementation(project(":mocks"))
            }
        }

        fun org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet.addCommonKotlinJvmSourceDir() {
            kotlin.srcDir("src/commonJvmAndroid/kotlin")
        }

        val jvmMain by getting {
            addCommonKotlinJvmSourceDir()
            dependencies {
                implementation(libs.ktor.okHttp)
            }
        }
        val jvmTest by getting
        val androidMain by getting {
            addCommonKotlinJvmSourceDir()
            dependencies {
                implementation(libs.ktor.okHttp)
            }
        }
        val appleMain by getting {
            dependencies {
                implementation(libs.ktor.iosHttp)
            }
        }
    }
}

android {
    testOptions.unitTests.all {
        it.enabled = false
    }
}
