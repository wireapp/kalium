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
import org.gradle.api.tasks.Sync
import org.gradle.api.attributes.Usage
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompileCommon

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    id(libs.plugins.kalium.library.get().pluginId)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

kaliumLibrary {
    multiplatform {
        enableJsTests.set(false)
        includeNativeInterop.set(true)
    }
}

val coreCryptoJvmNativeArtifacts by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

dependencies {
    coreCryptoJvmNativeArtifacts(libs.coreCryptoJvm)
}

// The shared implementation needs CoreCrypto's common declarations during metadata
// compilation. Keep this compiler-only input separate from the platform bindings:
// JVM and Android deliberately use their standalone generated bindings.
val coreCryptoCommonMetadata by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named("kotlin-metadata"))
        attribute(KotlinPlatformType.attribute, KotlinPlatformType.common)
    }
}
dependencies {
    coreCryptoCommonMetadata(libs.coreCryptoKmp)
}
val coreCryptoMetadataDirectory = layout.buildDirectory.dir("generated/coreCryptoCommonMetadata")
val extractCoreCryptoCommonMetadata by tasks.registering(Sync::class) {
    from({ coreCryptoCommonMetadata.map(::zipTree) }) {
        include("commonMain/**")
    }
    into(coreCryptoMetadataDirectory)
    doLast {
        check(destinationDir.resolve("commonMain/default/manifest").isFile) {
            "CoreCrypto metadata artifact no longer contains the expected commonMain KLIB"
        }
    }
}
tasks.withType<KotlinCompileCommon>().configureEach {
    if (name == "compileNonJsMainKotlinMetadata") {
        libraries.from(extractCoreCryptoCommonMetadata.map { it.destinationDir.resolve("commonMain") })
    }
}

val coreCryptoJvmNativeResources = layout.buildDirectory.dir("generated/coreCryptoJvmNativeResources")
val extractCoreCryptoJvmNativeResources by tasks.registering(Sync::class) {
    from({ coreCryptoJvmNativeArtifacts.map(::zipTree) }) {
        include("darwin-aarch64/**")
        include("linux-x86-64/**")
    }
    into(coreCryptoJvmNativeResources)
}

kotlin {
    iosArm64 {
        binaries.all {
            linkerOpts("-framework", "Security")
        }
    }
    iosSimulatorArm64 {
        binaries.all {
            linkerOpts("-framework", "Security")
        }
    }
    macosArm64 {
        binaries.all {
            linkerOpts("-framework", "Security")
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(projects.core.libsodium)
                api(projects.core.logger)
                // coroutines
                implementation(libs.coroutines.core)
                api(libs.ktor.core)

                // KTX
                implementation(libs.ktxDateTime)

                // Okio
                implementation(libs.okio.core)

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
        val nonJsMain by creating {
            dependsOn(commonMain)
            kotlin.srcDir("src/coreCryptoMain/kotlin")
        }
        val jvmMain by getting {
            dependsOn(nonJsMain)
            addCommonKotlinJvmSourceDir()
            dependencies {
                implementation(libs.coreCryptoJvm)
            }
            // Embed the native libraries carried by the JVM artifact as runtime resources.
            resources.srcDir(coreCryptoJvmNativeResources)
        }

        val jvmTest by getting {
            dependencies {
                implementation(libs.bouncycastle.pkix)
                implementation(libs.ktxSerialization)
            }
        }
        val androidMain by getting {
            dependsOn(nonJsMain)
            addCommonKotlinJvmSourceDir()
            dependencies {
                implementation(libs.androidCrypto)
                implementation(libs.coreCryptoAndroid.get().let { "${it.module}:${it.versionConstraint.requiredVersion}" }) {
                    exclude("androidx.core")
                    exclude("androidx.appcompat")
                }
            }
        }
        val appleMain by getting {
            dependsOn(nonJsMain)
            dependencies {
                implementation(libs.coreCryptoKmp)
            }
        }
        val jsMain by getting
        val jsTest by getting {
            dependencies {
                implementation(devNpm("fake-indexeddb", "6.2.5"))
            }
        }
    }
}

tasks.named("jvmProcessResources") {
    dependsOn(extractCoreCryptoJvmNativeResources)
}

project.appleTargets().forEach {
    registerCopyTestResourcesTask(it)
}
