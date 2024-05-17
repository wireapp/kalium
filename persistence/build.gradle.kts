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
    id(libs.plugins.sqldelight.get().pluginId)
    id(libs.plugins.kalium.library.get().pluginId)
}

kaliumLibrary {
    multiplatform {
        enableJsTests.set(false)
    }
}

dependencies {
    implementation(libs.kotlin.nativeUtils)
}

sqldelight {
    databases {
        create("UserDatabase") {
            dialect(libs.sqldelight.dialect.get().toString())
            packageName.set("com.wire.kalium.persistence")
            val sourceFolderName = "db_user"
            srcDirs.setFrom(listOf("src/commonMain/$sourceFolderName"))
            schemaOutputDirectory.set(file("src/commonMain/$sourceFolderName/schemas"))
        }

        create("GlobalDatabase") {
            dialect(libs.sqldelight.dialect.get().toString())
            packageName.set("com.wire.kalium.persistence")
            val sourceFolderName = "db_global"
            srcDirs.setFrom(listOf("src/commonMain/$sourceFolderName"))
            schemaOutputDirectory.set(file("src/commonMain/$sourceFolderName/schemas"))
        }
    }
}

kotlin {

    sourceSets {
        val commonMain by getting {
            dependencies {
                // coroutines
                api(libs.coroutines.core)

                api(libs.sqldelight.runtime)
                api(libs.sqldelight.coroutinesExtension)
                api(libs.sqldelight.primitiveAdapters)
                api(libs.ktxSerialization)
                api(libs.settings.kmp)
                api(libs.ktxDateTime)
                api(libs.sqldelight.androidxPaging)

                implementation(project(":util"))
                api(project(":persistence-api"))
                api(project(":logger"))
            }
        }
        val commonTest by getting {
            dependencies {
                // coroutines
                api(libs.coroutines.test)
                api(libs.turbine)
                // MultiplatformSettings
                api(libs.settings.kmpTest)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.sqldelight.jvmDriver)
                implementation(libs.sqlite.xerialDriver)
            }
        }
        val jvmTest by getting
        val jsMain by getting {
            dependencies {
                implementation(libs.sqldelight.jsDriver)
                implementation(npm("sql.js", "1.6.2"))
                implementation(devNpm("copy-webpack-plugin", "9.1.0"))
            }
        }
        val jsTest by getting
        val androidMain by getting {
            dependencies {
                implementation(libs.androidCrypto)
                implementation(libs.sqldelight.androidDriver)
                implementation(libs.sqlite.androidx)
                implementation(libs.sql.android.cipher)
            }
        }
        val appleMain by getting {
            dependencies {
                implementation(libs.sqldelight.nativeDriver)
            }
        }
    }
}

android {
    testOptions.unitTests.all {
        it.enabled = false
    }
}
