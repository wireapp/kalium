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
    alias(libs.plugins.kayan)
}

kaliumLibrary {
    multiplatform()
}

kayan {
    inheritFromRoot()
    packageName.set("com.wire.kalium.core.common.generated")
    className.set("KaliumCoreCommonBuildConfig")
    schema {
        include("use_unified_core_crypto")
    }
}

val useUnifiedCoreCryptoAndroid: Boolean = kayan.buildValue("use_unified_core_crypto", "android").asBoolean()
val useUnifiedCoreCryptoApple: Boolean = kayan.buildValue("use_unified_core_crypto", "apple").asBoolean()
val useUnifiedCoreCryptoJs: Boolean = kayan.buildValue("use_unified_core_crypto", "js").asBoolean()
val useUnifiedCoreCryptoJvm: Boolean = kayan.buildValue("use_unified_core_crypto", "jvm").asBoolean()

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.core.data)
                implementation(projects.core.logger)
                implementation(projects.core.util)
                implementation(projects.data.persistence)
                implementation(projects.data.network)
                implementation(projects.data.networkUtil)
                implementation(projects.core.cryptography)
                implementation(libs.ktxSerialization)
                implementation(libs.coroutines.core)
            }
        }

        fun org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet.addCommonKotlinJvmSourceDir() {
            kotlin.srcDir("src/commonJvmAndroid/kotlin")
        }

        val appleMain by getting {
            dependencies {
                if (useUnifiedCoreCryptoApple) {
                    implementation(libs.coreCryptoKmp)
                }
            }
        }

        val jsMain by getting {
            dependencies {
                if (useUnifiedCoreCryptoJs) {
                    implementation(libs.coreCryptoKmp)
                }
            }
        }

        val jvmMain by getting {
            addCommonKotlinJvmSourceDir()
            dependencies {
                implementation(libs.jna)
                if (useUnifiedCoreCryptoJvm) {
                    implementation(libs.coreCryptoKmp)
                } else {
                    implementation(libs.coreCryptoJvm)
                }
            }
        }

        val androidMain by getting {
            addCommonKotlinJvmSourceDir()
            dependencies {
                implementation(libs.work)
                if (useUnifiedCoreCryptoAndroid) {
                    implementation(libs.coreCryptoKmp)
                } else {
                    implementation(libs.coreCryptoAndroid.get().let { "${it.module}:${it.versionConstraint.requiredVersion}" }) {
                        exclude("androidx.core")
                        exclude("androidx.appcompat")
                    }
                }
            }
        }
    }
}
