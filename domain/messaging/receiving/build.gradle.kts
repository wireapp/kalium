/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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

import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest

plugins {
    alias(libs.plugins.kotlin.serialization)
    id(libs.plugins.kalium.library.get().pluginId)
    id("com.wire.kalium.apple-avs-runtime")
}

val generatedAppleKotlinDir = layout.buildDirectory.dir("generated/receiving/appleMain/kotlin")
val preparedCoreCryptoVersion = libs.coreCryptoKmp.get().versionConstraint.requiredVersion
val generateReceivingCompatibility by tasks.registering {
    inputs.property("coreCryptoVersion", preparedCoreCryptoVersion)
    outputs.dir(generatedAppleKotlinDir)
    doLast {
        val output = generatedAppleKotlinDir.get().asFile.resolve("com/wire/kalium/logic/sync/receiving/ReceivingCompatibility.kt")
        output.parentFile.mkdirs()
        output.writeText(
            "package com.wire.kalium.logic.sync.receiving\n\n" +
                "internal const val PREPARED_CORE_CRYPTO_VERSION = \"$preparedCoreCryptoVersion\"\n"
        )
    }
}

kaliumLibrary {
    multiplatform()
}

kotlin {
    iosSimulatorArm64 {
        binaries.executable("swiftFixture", listOf(NativeBuildType.DEBUG)) {
            compilation = compilations.getByName("test")
            baseName = "swift-receiving-fixture"
            entryPoint = "com.wire.kalium.logic.sync.receiving.runSwiftReceivingFixture"
        }
        binaries.configureEach {
            val entitlements = rootProject.file("core/cryptography/src/iosTest/entitlements.plist")
            linkerOpts("-framework", "Security", "-sectcreate", "__TEXT", "__entitlements", entitlements.absolutePath)
            linkTaskProvider.configure { inputs.file(entitlements) }
        }
    }
    sourceSets {
        appleMain {
            kotlin.srcDir(generateReceivingCompatibility)
        }
        commonMain.dependencies {
            api(projects.core.common)
            api(projects.core.data)
            api(projects.core.util)
            implementation(projects.core.cryptography)
            implementation(projects.data.persistence)
            implementation(projects.data.networkModel)
            implementation(projects.data.network)
            api(projects.core.failures)
            implementation(projects.data.dataMappers)
            implementation(libs.ktxSerialization)
            implementation(libs.ktxDateTime)
            implementation(libs.okio.core)
            implementation(libs.coroutines.core)
        }
        appleMain.dependencies {
            implementation(projects.domain.callingNotifications)
            implementation(projects.domain.usernetwork)
        }
        commonTest.dependencies {
            implementation(projects.data.persistenceTest)
            implementation(libs.coroutines.test)
        }
    }
}

// Receiving integration tests use the simulator Keychain for account tokens and CoreCrypto.
tasks.withType<KotlinNativeSimulatorTest>().configureEach {
    standalone.set(false)
}
