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
import org.jetbrains.kotlin.gradle.plugin.mpp.AbstractExecutable
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    alias(libs.plugins.kotlin.serialization)
    id(libs.plugins.kalium.library.get().pluginId)
    id(libs.plugins.kalium.mutation.testing.get().pluginId)
    id("com.wire.kalium.apple-avs-runtime")
    alias(libs.plugins.ksp)
    alias(libs.plugins.mokkery)
    alias(libs.plugins.skie)
}

kaliumLibrary {
    multiplatform {
        enableJs.set(false)
    }
}

mutationTesting {
    targetClasses.set(listOf("com.wire.kalium.logic.sync.receiver.*"))
    targetTests.set(listOf("com.wire.kalium.logic.sync.receiver.*"))
}

kotlin {
    explicitApi()
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation {
        filters {
            exclude {
                annotatedWith.add("com.wire.kalium.util.InternalKaliumApi")
                annotatedWith.add("com.wire.kalium.util.DebugKaliumApi")
            }
        }
    }

    iosSimulatorArm64 {
        binaries.executable("swiftAppFixture", listOf(NativeBuildType.DEBUG)) {
            compilation = compilations.getByName("test")
            baseName = "swift-app-verifier"
            entryPoint = "com.wire.kalium.logic.runSwiftReceivingAppVerifier"
        }
        // Test and fixture executables, not the KaliumLogic framework.
        binaries.withType<AbstractExecutable>().configureEach {
            val entitlements = rootProject.file("core/cryptography/src/iosTest/entitlements.plist")
            linkerOpts("-framework", "Security", "-sectcreate", "__TEXT", "__entitlements", entitlements.absolutePath)
            linkTaskProvider.configure { inputs.file(entitlements) }
        }
    }

    val xcf = XCFramework("KaliumLogic")
    val appleTargets = listOf(iosArm64(), iosSimulatorArm64(), macosArm64())
    appleTargets.forEach {
        it.binaries.framework {
            baseName = "KaliumLogic"
            freeCompilerArgs += "-Xbinary=bundleId=com.wire.kalium.logic"
            linkerOpts.add("-lsqlite3")
            xcf.add(this)
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.core.common)
                api(projects.domain.work)
                implementation(projects.data.network)
                api(projects.core.data)
                api(projects.data.dataMappers)
                api(projects.domain.messaging.receiving)
                api(projects.data.networkUtil)
                implementation(projects.core.cryptography)
                implementation(projects.data.persistence)
                implementation(projects.data.protobuf)
                api(projects.core.logger)
                api(projects.domain.calling)
                implementation(projects.core.util)
                implementation(projects.domain.cells)
                implementation(projects.domain.backup)
                implementation(projects.domain.userstorage)
                implementation(projects.domain.usernetwork)
                implementation(projects.domain.messaging.sending)

                // coroutines
                implementation(libs.coroutines.core)
                implementation(libs.ktxSerialization)
                implementation(libs.ktxDateTime)

                // ktor mockk engine
                implementation(libs.ktor.mock)

                // the Dependency is duplicated between here and persistence build.gradle.kts
                implementation(libs.settings.kmp)

                // Okio
                implementation(libs.okio.core)

                // HTML parsing
                implementation(libs.ksoup)

                implementation(libs.sqldelight.androidxPaging)
                api(libs.paging.common)
                // Concurrent collections
                implementation(libs.concurrentCollections)
                implementation(libs.statelyCommons)
                configurations.all {
                    exclude(group = "co.touchlab", module = "stately-strict-jvm")
                }
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(projects.core.common)
                implementation(projects.data.persistenceTest)
                implementation(projects.test.dataMocks)
                // coroutines
                implementation(libs.coroutines.test)
                implementation(libs.turbine)

                // mocking
                implementation(libs.okio.test)
                implementation(libs.settings.kmpTest)
            }
        }

        fun org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet.addCommonKotlinJvmSourceDir() {
            kotlin.srcDir("src/commonJvmAndroid/kotlin")
        }

        val appleMain by getting {
            kotlin.exclude("com/wire/kalium/logic/feature/call/**")
            dependencies {
                implementation(libs.coreCryptoKmp)
                // The app answers the share extension through its outbox (ADR 13).
                implementation(projects.share)
            }
        }
        val appleCallSourceDir = "src/appleMain/kotlin/com/wire/kalium/logic/feature/call"
        listOf(
            getByName("iosArm64Main"),
            getByName("iosSimulatorArm64Main"),
            getByName("macosArm64Main")
        ).forEach { appleTargetMain ->
            appleTargetMain.kotlin.srcDir(appleCallSourceDir)
            appleTargetMain.dependencies {
                implementation(libs.avsKmp)
            }
        }

        val jvmMain by getting {
            addCommonKotlinJvmSourceDir()
            dependencies {
                implementation(libs.jna)
                implementation(libs.coreCryptoJvm)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(libs.konsist)
            }
        }
        val androidMain by getting {
            addCommonKotlinJvmSourceDir()
            dependencies {
                implementation(libs.work)
                implementation(libs.coreCryptoAndroid.get().let { "${it.module}:${it.versionConstraint.requiredVersion}" }) {
                    exclude("androidx.core")
                    exclude("androidx.appcompat")
                }
            }
        }
        val androidHostTest by getting {
            dependencies {
                implementation(libs.robolectric)
                implementation(libs.core.ktx)
            }
        }
    }
}

// Account session integration tests use CoreCrypto and require the simulator Keychain service.
tasks.withType<KotlinNativeSimulatorTest>().configureEach {
    standalone.set(false)
}
