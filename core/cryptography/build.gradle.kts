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

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    id(libs.plugins.kalium.library.get().pluginId)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

kaliumLibrary {
    multiplatform {
        enableJs.set(true)
        includeNativeInterop.set(true)
    }
}

val coreCryptoJvmNativeArtifacts by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

dependencies {
    coreCryptoJvmNativeArtifacts(libs.coreCryptoJvmNatives)
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
    js {
        browser {
            testTask {
                useKarma {
                    useChromeHeadless()
                }
            }
        }
    }

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
            dependencies {
                implementation(libs.coreCryptoKmp)
            }
        }
        val jvmMain by getting {
            dependsOn(nonJsMain)
            addCommonKotlinJvmSourceDir()
            // core-crypto-kmp's JVM metadata does not attach Linux native resources.
            // Embed only the native files from the official target artifact, excluding its
            // duplicate binding classes from the JVM runtime classpath.
            resources.srcDir(coreCryptoJvmNativeResources)
        }

        val jvmTest by getting
        val androidMain by getting {
            dependsOn(nonJsMain)
            addCommonKotlinJvmSourceDir()
            dependencies {
                implementation(libs.androidCrypto)
            }
        }
        val appleMain by getting {
            dependsOn(nonJsMain)
        }
        val jsMain by getting {
            kotlin.srcDir("src/coreCryptoMain/kotlin")
            dependencies {
                implementation(npm("@wireapp/core-crypto", libs.versions.core.crypto.get()))
            }
        }
        val jsTest by getting
    }
}

tasks.named("jvmProcessResources") {
    dependsOn(extractCoreCryptoJvmNativeResources)
}

project.appleTargets().forEach {
    registerCopyTestResourcesTask(it)
}
