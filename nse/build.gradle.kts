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
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    alias(libs.plugins.skie)
    id("com.wire.kalium.apple-avs-runtime")
}

group = KaliumBuild.GROUP

skie { build { produceDistributableFramework() } }

kotlin {
    explicitApi()
    compilerOptions.optIn.add("com.wire.kalium.util.InternalKaliumApi")
    val framework = XCFramework("KaliumNSE")
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "KaliumNSE"
            freeCompilerArgs += "-Xbinary=bundleId=com.wire.kalium.nse"
            linkerOpts("-lsqlite3", "-framework", "Security", "-application_extension")
            framework.add(this)
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(projects.domain.messaging.receiving)
            implementation(projects.core.cryptography)
            implementation(projects.core.data)
            implementation(projects.core.common)
            implementation(projects.data.dataMappers)
            implementation(projects.data.persistence)
            implementation(projects.data.network)
            implementation(libs.coroutines.core)
        }
    }
}
