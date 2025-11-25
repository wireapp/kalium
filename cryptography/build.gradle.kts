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

import com.wire.kalium.plugins.appleTargets

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    id(libs.plugins.kalium.library.get().pluginId)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.mockative)
}

kaliumLibrary {
    multiplatform {
        includeNativeInterop.set(true)
    }
}

// Swift wrapper compilation configuration
val swiftWrapperSourceDir = file("src/swift/CoreCryptoWrapper")
val swiftWrapperBuildDir = layout.buildDirectory.dir("swift").get().asFile

// Swift target configuration: Kotlin target name -> (SDK name, target triple, XCFramework slice)
data class SwiftTargetConfig(
    val sdkName: String,
    val targetTriple: String,
    val xcframeworkSlice: String
)

val swiftTargetConfigs = mapOf(
    "iosArm64" to SwiftTargetConfig("iphoneos", "arm64-apple-ios16.6", "ios-arm64"),
    "iosX64" to SwiftTargetConfig("iphonesimulator", "x86_64-apple-ios16.6-simulator", "ios-arm64-simulator"),
    "iosSimulatorArm64" to SwiftTargetConfig("iphonesimulator", "arm64-apple-ios16.6-simulator", "ios-arm64-simulator")
)

// Register Swift compilation tasks for each target
swiftTargetConfigs.forEach { (targetName, config) ->
    val taskName = "compileSwiftWrapper${targetName.replaceFirstChar { it.uppercase() }}"
    val outputDir = File(swiftWrapperBuildDir, targetName)

    tasks.register<Exec>(taskName) {
        group = "swift"
        description = "Compiles Swift wrapper for $targetName"

        val coreCryptoFrameworkPath = "${projectDir}/frameworks/WireCoreCrypto.xcframework/${config.xcframeworkSlice}"
        val uniffiFrameworkPath = "${projectDir}/frameworks/WireCoreCryptoUniffi.xcframework/${config.xcframeworkSlice}"

        inputs.dir(swiftWrapperSourceDir)
        inputs.dir(coreCryptoFrameworkPath)
        inputs.dir(uniffiFrameworkPath)
        outputs.dir(outputDir)

        doFirst {
            outputDir.mkdirs()
        }

        val swiftFiles = fileTree(swiftWrapperSourceDir) {
            include("**/*.swift")
            exclude("Package.swift")
        }.files.map { it.absolutePath }

        val sdkPath = "xcrun --sdk ${config.sdkName} --show-sdk-path".let { cmd ->
            providers.exec {
                commandLine("sh", "-c", cmd)
            }.standardOutput.asText.get().trim()
        }

        commandLine(
            "swiftc",
            "-emit-library",
            "-emit-objc-header",
            "-emit-objc-header-path", "${outputDir}/CoreCryptoWrapper.h",
            "-module-name", "CoreCryptoWrapper",
            "-sdk", sdkPath,
            "-target", config.targetTriple,
            "-F", coreCryptoFrameworkPath,
            "-F", uniffiFrameworkPath,
            "-framework", "WireCoreCrypto",
            "-framework", "WireCoreCryptoUniffi",
            "-o", "${outputDir}/libCoreCryptoWrapper.a",
            *swiftFiles.toTypedArray()
        )
    }
}

kotlin {
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { target ->
        val targetName = target.targetName
        val config = swiftTargetConfigs[targetName]!!
        val swiftOutputDir = File(swiftWrapperBuildDir, targetName)
        val coreCryptoFrameworkPath = "${projectDir}/frameworks/WireCoreCrypto.xcframework/${config.xcframeworkSlice}/WireCoreCrypto.framework"
        val uniffiFrameworkPath = "${projectDir}/frameworks/WireCoreCryptoUniffi.xcframework/${config.xcframeworkSlice}/WireCoreCryptoUniffi.framework"

        val compileSwiftTask = tasks.named("compileSwiftWrapper${targetName.replaceFirstChar { it.uppercase() }}")

        target.binaries.all {
            linkerOpts("-framework", "Security")
            linkerOpts("-F", "${projectDir}/frameworks/WireCoreCrypto.xcframework/${config.xcframeworkSlice}")
            linkerOpts("-F", "${projectDir}/frameworks/WireCoreCryptoUniffi.xcframework/${config.xcframeworkSlice}")
            linkerOpts("-framework", "WireCoreCrypto")
            linkerOpts("-framework", "WireCoreCryptoUniffi")
            linkerOpts("-L", swiftOutputDir.absolutePath)
            linkerOpts("-lCoreCryptoWrapper")
        }
        target.compilations.getByName("main") {
            compileTaskProvider.configure {
                dependsOn(compileSwiftTask)
            }
            cinterops {
                create("CoreCryptoWrapper") {
                    defFile(file("src/nativeInterop/cinterop/CoreCryptoWrapper.def"))
                    includeDirs(swiftOutputDir.absolutePath)
                    compilerOpts(
                        "-F$coreCryptoFrameworkPath/..",
                        "-F$uniffiFrameworkPath/.."
                    )
                    tasks.named(interopProcessingTaskName) {
                        dependsOn(compileSwiftTask)
                    }
                }
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.logger)
                // coroutines
                implementation(libs.coroutines.core)
                api(libs.ktor.core)

                // KTX
                implementation(libs.ktxDateTime)

                // Okio
                implementation(libs.okio.core)

                // Libsodium
                implementation(libs.libsodiumBindingsMP)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.coroutines.test)
                implementation(libs.okio.test)
            }
        }

        fun org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet.addCommonKotlinJvmSourceDir() {
            kotlin.srcDir("src/commonJvmAndroid/kotlin")
        }

        val jvmMain by getting {
            addCommonKotlinJvmSourceDir()
            dependencies {
                implementation(libs.coreCryptoJvm)
            }
        }
        val jvmTest by getting
        val jsMain by getting {
            dependencies {
                implementation(npm("@wireapp/store-engine", "4.9.9"))
            }
        }
        val jsTest by getting
        val androidMain by getting {
            addCommonKotlinJvmSourceDir()
            dependencies {
                implementation(libs.androidCrypto)
                implementation(libs.coreCryptoAndroid.get().let { "${it.module}:${it.versionConstraint.requiredVersion}" }) {
                    exclude("androidx.core")
                    exclude("androidx.appcompat")
                }
            }
        }
        val appleMain by getting
    }
}

project.appleTargets().forEach {
    registerCopyTestResourcesTask(it)
}

// Copy frameworks for iOS test execution
// The test binary needs the frameworks at @executable_path/Frameworks at runtime
listOf("iosArm64", "iosSimulatorArm64").forEach { targetName ->
    val config = swiftTargetConfigs[targetName]!!
    val capitalizedTarget = targetName.replaceFirstChar { it.uppercase() }

    tasks.register<Copy>("copyFrameworksFor${capitalizedTarget}Test") {
        group = "swift"
        description = "Copies CoreCrypto frameworks for $targetName test execution"

        val coreCryptoFrameworkPath = "${projectDir}/frameworks/WireCoreCrypto.xcframework/${config.xcframeworkSlice}/WireCoreCrypto.framework"
        val uniffiFrameworkPath = "${projectDir}/frameworks/WireCoreCryptoUniffi.xcframework/${config.xcframeworkSlice}/WireCoreCryptoUniffi.framework"

        from(coreCryptoFrameworkPath) {
            into("WireCoreCrypto.framework")
        }
        from(uniffiFrameworkPath) {
            into("WireCoreCryptoUniffi.framework")
        }
        into(layout.buildDirectory.dir("bin/$targetName/debugTest/Frameworks"))
    }

    tasks.matching { it.name == "${targetName}Test" }.configureEach {
        dependsOn("copyFrameworksFor${capitalizedTarget}Test")
    }
}

// SwiftKlib configuration removed - using direct cinterop with WireCoreCrypto XCFrameworks
// The XCFrameworks already expose Obj-C compatible APIs via UniFFI bindings

android {
    testOptions.unitTests.all {
        it.enabled = false
    }
    defaultConfig {
        ndk {
            abiFilters.apply {
                add("armeabi-v7a")
                add("arm64-v8a")
                add("x86_64")
            }
        }
    }
}
