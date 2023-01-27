/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.util.targets


fun Project.darwinTargets(): List<String> =
    listOf(
        "iosX64",
        "iosArm64",
        "iosSimulatorArm64",
        "macosX64",
        "macosArm64"
    )

fun KotlinMultiplatformExtension.commonDarwinMultiplatformConfig() {
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    macosX64()
    macosArm64()

    val commonMain = sourceSets.getByName("commonMain")
    val commonTest = sourceSets.getByName("commonTest")
    val darwinMain = sourceSets.create("darwinMain") {
        dependsOn(commonMain)
    }
    val darwinTest = sourceSets.create("darwinTest") {
        dependsOn(commonTest)
    }
    val iosX64Main = sourceSets.getByName("iosX64Main") {
        dependsOn(darwinMain)
    }
    val iosX64Test = sourceSets.getByName("iosX64Test") {
        dependsOn(darwinTest)
    }
    val iosArm64Main = sourceSets.getByName("iosArm64Main") {
        dependsOn(darwinMain)
    }
    val iosArm64Test = sourceSets.getByName("iosArm64Test") {
        dependsOn(darwinTest)
    }
    val iosSimulatorArm64Main = sourceSets.getByName("iosSimulatorArm64Main") {
        dependsOn(darwinMain)
    }
    val iosSimulatorArm64Test = sourceSets.getByName("iosSimulatorArm64Test") {
        dependsOn(darwinTest)
    }
    val macosX64Main = sourceSets.getByName("macosX64Main") {
        dependsOn(darwinMain)
    }
    val macosX64Test = sourceSets.getByName("macosX64Test") {
        dependsOn(darwinTest)
    }
    val macosArm64Main = sourceSets.getByName("macosArm64Main") {
        dependsOn(darwinMain)
    }
    val macosArm64Test = sourceSets.getByName("macosArm64Test") {
        dependsOn(darwinTest)
    }
}
