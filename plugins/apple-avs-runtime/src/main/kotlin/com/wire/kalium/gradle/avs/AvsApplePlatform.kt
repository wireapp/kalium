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

package com.wire.kalium.gradle.avs

internal enum class AvsApplePlatform(
    val kotlinTargetName: String,
    val xcodePlatformPrefix: String,
    val xcframeworkSlice: String,
    val xcodeRunpathSearchPath: String,
) {
    IOS_DEVICE(
        kotlinTargetName = "iosArm64",
        xcodePlatformPrefix = "iphoneos",
        xcframeworkSlice = "ios-arm64",
        xcodeRunpathSearchPath = "@executable_path/Frameworks",
    ),
    IOS_SIMULATOR(
        kotlinTargetName = "iosSimulatorArm64",
        xcodePlatformPrefix = "iphonesimulator",
        xcframeworkSlice = "ios-arm64_x86_64-simulator",
        xcodeRunpathSearchPath = "@executable_path/Frameworks",
    ),
    MACOS(
        kotlinTargetName = "macosArm64",
        xcodePlatformPrefix = "macosx",
        xcframeworkSlice = "macos-arm64_x86_64",
        xcodeRunpathSearchPath = "@executable_path/../Frameworks",
    ),
    ;

    companion object {
        fun forKotlinTarget(name: String): AvsApplePlatform? =
            entries.firstOrNull { it.kotlinTargetName == name }

        fun forXcodePlatform(name: String): AvsApplePlatform? =
            entries.firstOrNull { name.startsWith(it.xcodePlatformPrefix) }
    }
}
