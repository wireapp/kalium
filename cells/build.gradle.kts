@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

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
    id(libs.plugins.kalium.library.get().pluginId)
    alias(libs.plugins.ksp)
    alias(libs.plugins.mockative)
}

kaliumLibrary {
    multiplatform { enableJs.set(false) }
}

kotlin {
    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }
    explicitApi()
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(projects.common)
                implementation(projects.network)
                implementation(projects.data)
                implementation(projects.util)
                implementation(projects.persistence)
                implementation(libs.coroutines.core)
                implementation(libs.ktor.authClient)
                implementation(libs.okio.core)
                implementation(libs.sqldelight.androidxPaging)
                implementation(libs.wire.cells.sdk)
            }
        }
        val commonTest by getting {
            dependencies {
                // coroutines
                implementation(libs.coroutines.test)
                implementation(libs.turbine)
                // ktor test
                implementation(libs.ktor.mock)
                // mocks
                implementation(libs.okio.test)
            }
        }

        fun org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet.addCommonKotlinJvmSourceDir() {
            kotlin.srcDir("src/commonJvmAndroid/kotlin")
        }

        val jvmMain by getting {
            addCommonKotlinJvmSourceDir()
            dependencies {
                implementation(libs.ktor.okHttp)
                implementation(awssdk.services.s3)
            }
        }
        val androidMain by getting {
            addCommonKotlinJvmSourceDir()
            dependencies {
                implementation(libs.ktor.okHttp)
                implementation(awssdk.services.s3)
            }
        }
    }
}
