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
    .maxDepth(3)
    .filter { it != rootDir }
    .filter {
        it.name != "buildSrc" && it.isDirectory &&
                file("${it.absolutePath}/build.gradle.kts").exists()
    }.forEach {
        val projectPath = it.relativeTo(rootDir).path.replace(File.separator, ":")
        include(":$projectPath")
    }

val sqlDelightBuildPath = "vendor/sqldelight"
val sqlDelightModules = mapOf(
    "app.cash.sqldelight:gradle-plugin" to ":sqldelight-gradle-plugin",
    "app.cash.sqldelight:runtime" to ":runtime",
    "app.cash.sqldelight:coroutines-extensions" to ":extensions:coroutines-extensions",
    "app.cash.sqldelight:android-driver" to ":drivers:android-driver",
    "app.cash.sqldelight:androidx-paging3-extensions" to ":extensions:androidx-paging3",
    "app.cash.sqldelight:native-driver" to ":drivers:native-driver",
    "app.cash.sqldelight:sqlite-driver" to ":drivers:sqlite-driver",
    "app.cash.sqldelight:web-worker-driver" to ":drivers:web-worker-driver",
    "app.cash.sqldelight:primitive-adapters" to ":adapters:primitive-adapters",
    "app.cash.sqldelight:compiler-env" to ":sqldelight-compiler:environment",
    "app.cash.sqldelight:migration-env" to ":sqlite-migrations:environment",
    "app.cash.sqldelight:sqlite-3-38-dialect" to ":dialects:sqlite-3-38",
    "app.cash.sqldelight:postgresql-dialect" to ":dialects:postgresql",
    "app.cash.sqldelight:r2dbc-driver" to ":drivers:r2dbc-driver",
    "app.cash.sqldelight:async-extensions" to ":extensions:async-extensions",
)

fun org.gradle.api.initialization.ConfigurableIncludedBuild.useLocalSqlDelightFork() {
    dependencySubstitution {
        sqlDelightModules.forEach { (moduleCoordinates, projectPath) ->
            substitute(module(moduleCoordinates)).using(project(projectPath))
        }
    }
}

pluginManagement {
    includeBuild("vendor/sqldelight")

    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

includeBuild(sqlDelightBuildPath) {
    useLocalSqlDelightFork()
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("awssdk") {
            from("aws.sdk.kotlin:version-catalog:1.5.89")
        }
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
