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
    id("org.jetbrains.compose") version "1.7.0"
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}


dependencies {
    implementation(project(":backup"))
    implementation(compose.desktop.currentOs)
    implementation(kotlin("reflect"))
    implementation(libs.compose.fileKit.core)
    implementation(libs.compose.fileKit.compose)
    implementation(libs.coroutines.core)
    implementation(libs.ktxSerialization)
//     implementation("org.slf4j:slf4j-log4j12:2.14.1")
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
