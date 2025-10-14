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
    alias(libs.plugins.kotlin.serialization)
    id(libs.plugins.kalium.library.get().pluginId)
}

kaliumLibrary {
    multiplatform()
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.data)
                implementation(projects.logger)
                implementation(projects.util)
                implementation(projects.persistence)
                implementation(projects.network)
                implementation(projects.networkUtil)
                implementation(projects.cryptography)
                implementation(libs.ktxSerialization)
                implementation(libs.coroutines.core)
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
    }
}
