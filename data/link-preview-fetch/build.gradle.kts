/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
}

kaliumLibrary {
    multiplatform {
        enableJs.set(false)
    }
}

kotlin {
    explicitApi()
    sourceSets {
        all {
            languageSettings.optIn("com.wire.kalium.util.InternalKaliumApi")
        }
        commonMain.dependencies {
            implementation(projects.core.util)
            implementation(libs.coroutines.core)
            implementation(libs.ktor.core)
            implementation(libs.okio.core)
        }
        commonTest.dependencies {
            implementation(libs.coroutines.test)
            implementation(libs.ktor.mock)
        }
        val appleMain by getting {
            dependencies {
                implementation(libs.ktor.httpCio)
            }
        }
        val jvmMain by getting {
            kotlin.srcDir("src/commonJvmAndroid/kotlin")
            dependencies {
                implementation(libs.ktor.okHttp)
            }
        }
        val androidMain by getting {
            kotlin.srcDir("src/commonJvmAndroid/kotlin")
            dependencies {
                implementation(libs.ktor.okHttp)
            }
        }
    }
}
