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
    id(libs.plugins.android.library.get().pluginId)
    id(libs.plugins.kotlin.multiplatform.get().pluginId)
    id(libs.plugins.kalium.library.get().pluginId)
}

kaliumLibrary {
    multiplatform { enableJs.set(false) }
}

kotlin {
    explicitApi()
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":network"))
                implementation(project(":util"))
                implementation(project(":logic"))
                implementation(libs.coroutines.core)
                implementation(libs.ktor.authClient)
                implementation(libs.okio.core)
                implementation(libs.wire.cells.sdk)
            }
        }
        commonTest {
            dependencies {
            }
        }
        androidMain {
            dependencies {
                implementation(libs.ktor.okHttp)
                implementation(awssdk.services.s3)
            }
        }
    }
}
