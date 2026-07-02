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
    id(libs.plugins.kalium.library.get().pluginId)
    alias(libs.plugins.ksp)
}

kaliumLibrary {
    multiplatform {
        enableJs.set(false)
    }
}

kotlin {
    val disableAppleAvs: Boolean = findProperty("kalium.disableAppleAvs")?.toString()?.toBoolean() ?: false

    fun org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet.addCommonKotlinJvmSourceDir() {
        kotlin.srcDir("src/commonJvmAndroid/kotlin")
    }

    sourceSets {
        val androidDeviceTest by getting {
            dependencies {
                implementation(libs.androidtest.runner)
                implementation(libs.androidtest.rules)
            }
        }
        val androidMain by getting {
            addCommonKotlinJvmSourceDir()
            dependencies {
                api(libs.avs)
                api(
                    libs.jna.map {
                    project.dependencies.create(
                        it,
                        closureOf<ExternalModuleDependency> {
                        artifact {
                            type = "aar"
                        }
                    }
                    )
                }
                )
            }
        }
        val jvmMain by getting {
            addCommonKotlinJvmSourceDir()
            dependencies {
                implementation(libs.jna)
            }
        }
        val appleMain by getting
        if (disableAppleAvs) {
            val appleNoAvsMainSourceDir = "src/appleNoAvsMain/kotlin"
            listOf(
                getByName("iosArm64Main"),
                getByName("iosSimulatorArm64Main"),
                getByName("macosArm64Main")
            ).forEach { appleTargetMain ->
                appleTargetMain.kotlin.srcDir(appleNoAvsMainSourceDir)
            }
        } else {
            val appleAvsMainSourceDir = "src/appleAvsMain/kotlin"
            listOf(
                getByName("iosArm64Main"),
                getByName("iosSimulatorArm64Main"),
                getByName("macosArm64Main")
            ).forEach { appleTargetMain ->
                appleTargetMain.kotlin.srcDir(appleAvsMainSourceDir)
                appleTargetMain.dependencies {
                    implementation(libs.avsKmp)
                }
            }
            val appleAvsIosMainSourceDir = "src/appleAvsIosMain/kotlin"
            listOf(
                getByName("iosArm64Main"),
                getByName("iosSimulatorArm64Main")
            ).forEach { iosTargetMain ->
                iosTargetMain.kotlin.srcDir(appleAvsIosMainSourceDir)
            }
            getByName("macosArm64Main").kotlin.srcDir("src/appleAvsMacosMain/kotlin")
        }

        val commonTest by getting {
            dependencies { }
        }
    }
}
