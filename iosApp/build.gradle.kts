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
    id(libs.plugins.kotlin.multiplatform.get().pluginId)
}

kotlin {
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "KaliumNetwork"
            isStatic = false  // Dynamic framework to share Kotlin runtime with other frameworks

            // Export the network module and its dependencies for Swift access
            export(projects.network)
            export(projects.networkModel)
            export(projects.logger)
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                // Re-export these modules so they're available in the framework
                api(projects.network)
                api(projects.networkModel)
                api(projects.logger)
            }
        }
    }
}

// Task to assemble the XCFramework for distribution
tasks.register("assembleKaliumNetworkXCFramework") {
    group = "build"
    description = "Assembles the KaliumNetwork XCFramework for iOS"

    dependsOn(
        "linkReleaseFrameworkIosArm64",
        "linkReleaseFrameworkIosX64",
        "linkReleaseFrameworkIosSimulatorArm64"
    )

    doLast {
        val outputDir = layout.buildDirectory.dir("XCFrameworks/release").get().asFile
        outputDir.mkdirs()

        val xcframeworkPath = File(outputDir, "KaliumNetwork.xcframework")
        if (xcframeworkPath.exists()) {
            xcframeworkPath.deleteRecursively()
        }

        val arm64Framework = layout.buildDirectory.file("bin/iosArm64/releaseFramework/KaliumNetwork.framework").get().asFile
        val x64Framework = layout.buildDirectory.file("bin/iosX64/releaseFramework/KaliumNetwork.framework").get().asFile
        val simulatorArm64Framework = layout.buildDirectory.file("bin/iosSimulatorArm64/releaseFramework/KaliumNetwork.framework").get().asFile

        exec {
            commandLine(
                "xcodebuild",
                "-create-xcframework",
                "-framework", arm64Framework.absolutePath,
                "-framework", x64Framework.absolutePath,
                "-framework", simulatorArm64Framework.absolutePath,
                "-output", xcframeworkPath.absolutePath
            )
        }

        println("XCFramework created at: ${xcframeworkPath.absolutePath}")
    }
}
