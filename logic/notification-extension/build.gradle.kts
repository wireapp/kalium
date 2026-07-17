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

    val xcFramework = XCFramework("KaliumNotificationExtension")
    listOf(iosArm64(), iosSimulatorArm64(), macosArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "KaliumNotificationExtension"
            isStatic = false
            freeCompilerArgs += "-Xbinary=bundleId=com.wire.kalium.notification-extension"
            linkerOpts("-lsqlite3")
            xcFramework.add(this)
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(projects.data.messageContent)
                implementation(projects.data.notificationInbox)
                implementation(projects.data.protobuf)
                implementation(projects.domain.messaging.receiving)
                implementation(projects.domain.notificationSync)
                implementation(libs.coroutines.core)
                implementation(libs.ktxDateTime)
            }
        }
        val appleMain by getting {
            dependencies {
                implementation(projects.data.syncCoordination)
                // Spike-only: reuse the authenticated session and CoreCrypto graph that already
                // exists in :logic. This deliberately trades framework size for a real end-to-end
                // account path while the narrow production graph is still being extracted.
                implementation(projects.logic)
            }
        }
    }
}
