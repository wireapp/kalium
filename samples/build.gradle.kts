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
    id(libs.plugins.kalium.library.get().pluginId)
}

kaliumLibrary {
    multiplatform {
        enableJs.set(true)
    }
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":backup"))
                implementation(project(":cryptography"))
                implementation(project(":protobuf"))
                implementation(project(":logger"))

                implementation(libs.coroutines.core)
                implementation(libs.okio.core)
            }
        }

        val nonJsMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation(project(":network"))
                implementation(project(":persistence"))
            }
        }

        val jvmMain by getting {
            dependsOn(nonJsMain)
            dependencies {
                implementation(project(":logic"))
                implementation(project(":calling"))
            }
        }

        val androidMain by getting {
            dependsOn(nonJsMain)
            dependencies {
                implementation(project(":logic"))
                implementation(project(":calling"))
            }
        }

        val appleMain by getting {
            dependsOn(nonJsMain)
            dependencies {
                implementation(project(":logic"))
                implementation(project(":calling"))
            }
        }
    }
}
