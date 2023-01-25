/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
    database("UserDatabase") {
        dialect(libs.sqldelight.dialect.get().toString())
        packageName = "com.wire.kalium.persistence"
        val sourceFolderName = "db_user"
        sourceFolders = listOf(sourceFolderName)
        schemaOutputDirectory = file("src/commonMain/$sourceFolderName/schemas")
    }

    database("GlobalDatabase") {
        dialect(libs.sqldelight.dialect.get().toString())
        packageName = "com.wire.kalium.persistence"
        val sourceFolderName = "db_global"
        sourceFolders = listOf(sourceFolderName)
        schemaOutputDirectory = file("src/commonMain/$sourceFolderName/schemas")
    }
}

kotlin {

    sourceSets {
        val commonMain by getting {
            dependencies {
                // coroutines
                implementation(libs.coroutines.core)

                implementation(libs.sqldelight.runtime)
                implementation(libs.sqldelight.coroutinesExtension)
                implementation(libs.sqldelight.primitiveAdapters)
                implementation(libs.ktxSerialization)
                implementation(libs.settings.kmp)
                implementation(libs.ktxDateTime)

                implementation(project(":util"))
                api(project(":logger"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                // coroutines
                implementation(libs.coroutines.test)
                implementation(libs.turbine)
                // MultiplatformSettings
                implementation(libs.settings.kmpTest)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.sqldelight.jvmDriver)
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
                implementation(libs.securityCrypto)
                implementation(libs.sqldelight.androidDriver)
                implementation(libs.sqldelight.androidxPaging)
                implementation(libs.paging3)
                implementation(libs.sqlite.androidx)
                implementation(libs.sql.android.cipher)
            }
        }

        val iosX64Main by getting {
            dependencies {
                implementation(libs.sqldelight.nativeDriver)
            }
        }
        val iosX64Test by getting
    }
}
