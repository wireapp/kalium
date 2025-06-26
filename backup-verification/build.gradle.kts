/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
    kotlin("jvm")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.jetbrains)
}

dependencies {
    implementation(project(":backup"))
    implementation(compose.desktop.currentOs)
    implementation(compose.materialIconsExtended)
    implementation(kotlin("reflect"))
    implementation(libs.compose.fileKit.core)
    implementation(libs.compose.fileKit.compose)
    implementation(libs.coroutines.core)
    implementation(libs.ktxSerialization)
}

compose.desktop {
    application {
        mainClass = "MainKt"
        nativeDistributions {
            linux {
                modules("jdk.security.auth")
            }
        }
    }
}
