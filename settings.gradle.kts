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
        // temporary repo containing mockative 3.0.1 with a fix for a bug https://github.com/mockative/mockative/issues/143
        // until mockative releases a new version with a proper fix
        maven(url = "https://raw.githubusercontent.com/saleniuk/mockative/fix/duplicates-while-merging-dex-archives-mvn/release")
        gradlePluginPortal()
        google()
        mavenCentral()
    }

    // If it is a F-droid release, delete these lines. Deleting `useVersion(...)` should be enough.
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id.contains("io.mockative") && requested.version == "3.0.1") {
                println("REPLACING MOCKATIVE WITH FIX. This should NOT happen on F-Droid builds!")
                useVersion("3.0.1-fix")
            }
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("awssdk") {
            from("aws.sdk.kotlin:version-catalog:1.3.112")
        }
    }
}
