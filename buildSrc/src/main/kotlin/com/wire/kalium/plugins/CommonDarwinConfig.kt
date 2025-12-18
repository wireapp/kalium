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

package com.wire.kalium.plugins

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable

fun Project.appleTargets(): List<String> =
    listOf(
        "iosArm64",
        "iosSimulatorArm64",
        "macosArm64"
    )

fun KotlinMultiplatformExtension.commonAppleMultiplatformConfig() {
    iosArm64()
    iosSimulatorArm64()
    macosArm64()

    targets
        .filterIsInstance<KotlinNativeTarget>()
        .flatMap { it.binaries }
        .forEach { compilationUnit ->
            compilationUnit.linkerOpts("-lsqlite3")
            // Configure Stop-The-World with Mark and Sweep GC for iOS Simulator ARM64 tests
            // to work around test execution issues
            if (compilationUnit is TestExecutable && compilationUnit.target.name == "iosSimulatorArm64") {
                compilationUnit.binaryOptions["gc"] = "stwms"
            }
        }
}
