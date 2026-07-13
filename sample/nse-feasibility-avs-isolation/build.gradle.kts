/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    id(libs.plugins.kalium.library.get().pluginId)
}

kaliumLibrary {
    multiplatform {
        enableJs.set(false)
    }
}

kotlin {
    explicitApi()

    val xcFramework = XCFramework("KaliumNseAvsIsolation")
    val iosDevice = iosArm64()
    val iosSimulator = iosSimulatorArm64()
    val macos = macosArm64()

    listOf(iosDevice, iosSimulator, macos).forEach { target ->
        target.binaries.framework {
            baseName = "KaliumNseAvsIsolation"
            isStatic = false
            freeCompilerArgs += "-Xbinary=bundleId=com.wire.kalium.nse.feasibility.avs"
            xcFramework.add(this)
        }
    }

    sourceSets {
        val appleMain by getting {
            dependencies {
                implementation(projects.domain.callingNotifications)
            }
        }
    }
}
