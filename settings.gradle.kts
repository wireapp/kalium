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

// Work-around to make dependency resolution work with Multiplatform Composite Builds
// We can uncomment the project name once the issue is fixed.
// See: https://youtrack.jetbrains.com/issue/KT-56536
// rootProject.name = "Kalium"

// Assume that all folders that contain a build.gradle.kts and are not buildSrc should be included
rootDir
    .walk()
    .maxDepth(1)
    .filter {
        it.name != "buildSrc" && it.isDirectory &&
                file("${it.absolutePath}/build.gradle.kts").exists()
    }.forEach {
        include(":${it.name}")
    }

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
