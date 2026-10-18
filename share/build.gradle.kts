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
    id("org.jetbrains.kotlin.multiplatform")
    alias(libs.plugins.skie)
    alias(libs.plugins.kotlin.serialization)
    id("com.wire.kalium.apple-avs-runtime")
}

group = KaliumBuild.GROUP

skie { build { produceDistributableFramework() } }

kotlin {
    explicitApi()
    compilerOptions.optIn.add("com.wire.kalium.util.InternalKaliumApi")
    val framework = XCFramework("KaliumShare")
    val simulatorOnlyXcFramework = providers
        .gradleProperty("wire.ios.simulatorOnly")
        .map(String::toBooleanStrict)
        .getOrElse(false)
    val deviceOnlyXcFramework = providers
        .gradleProperty("wire.ios.deviceOnly")
        .map(String::toBooleanStrict)
        .getOrElse(false)
    require(!(simulatorOnlyXcFramework && deviceOnlyXcFramework)) {
        "Select either simulator-only or device-only XCFrameworks"
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        val includeInXcFramework = when {
            simulatorOnlyXcFramework -> target.name == "iosSimulatorArm64"
            deviceOnlyXcFramework -> target.name == "iosArm64"
            else -> true
        }
        target.binaries.framework {
            baseName = "KaliumShare"
            freeCompilerArgs += "-Xbinary=bundleId=com.wire.kalium.share"
            linkerOpts("-lsqlite3", "-framework", "Security", "-application_extension")
            if (includeInXcFramework) {
                framework.add(this)
            }
        }
    }
    // Share rules run against a real account database on macOS.
    macosArm64()
    sourceSets {
        commonMain.dependencies {
            implementation(projects.domain.messaging.receiving)
            implementation(projects.domain.messaging.sending)
            implementation(projects.core.cryptography)
            implementation(projects.core.data)
            implementation(projects.core.common)
            implementation(projects.core.logger)
            implementation(projects.data.dataMappers)
            implementation(projects.data.persistence)
            implementation(projects.data.network)
            implementation(projects.data.networkModel)
            implementation(libs.coroutines.core)
            implementation(libs.okio.core)
            implementation(libs.ktxSerialization)
        }
        appleTest.dependencies {
            implementation(kotlin("test"))
            implementation(projects.data.persistenceTest)
            implementation(libs.coroutines.test)
        }
    }
}
