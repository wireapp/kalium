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

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    id(libs.plugins.android.library.get().pluginId)
    id(libs.plugins.kotlin.multiplatform.get().pluginId)
    id(libs.plugins.kalium.library.get().pluginId)
}

kaliumLibrary {
    multiplatform {
        enableJsTests.set(false)
    }
}

val codegenProject = project(":protobuf-codegen")
val generatedFilesBaseDir = file("generated")
generatedFilesBaseDir.mkdirs()

kotlin {
    sourceSets {
        val commonMain by getting {
            kotlin.srcDir(generatedFilesBaseDir)
            dependencies {
                api(libs.pbandk.runtime.common)
            }
        }
        val commonTest by getting {
            dependencies { }
        }
        val androidUnitTest by getting {
            dependencies {
                implementation(libs.androidtest.runner)
                implementation(libs.androidtest.rules)
            }
        }
        val iosX64Main by getting {
            dependencies {
                api(libs.pbandk.runtime.iosX64)
            }
        }
        val iosArm64Main by getting {
            dependencies {
                api(libs.pbandk.runtime.iosArm64)
            }
        }
        val iosSimulatorArm64Main by getting {
            dependencies {
                api(libs.pbandk.runtime.iosSimulatorArm64)
            }
        }
        val macosX64Main by getting {
            dependencies {
                api(libs.pbandk.runtime.macX64)
            }
        }
        val macosArm64Main by getting {
            dependencies {
                api(libs.pbandk.runtime.macArm64)
            }
        }
    }
}

val compileTasks = tasks.matching { it is KotlinCompile || it is KotlinNativeCompile }

tasks.register("setupProtoTools") {
    providers.exec {
        commandLine("sh")
        args = listOf("$rootDir/scripts/setup_proto_tools.sh")
    }.standardOutput.asText.get().trim()
}

tasks.register("generateProto") {
    dependsOn("setupProtoTools")
    providers.exec {
        commandLine("sh")
        args = listOf("$rootDir/scripts/protoc_gen_kotlin.sh")
    }.standardOutput.asText.get().trim()
}

compileTasks.forEach { task ->
    task.dependsOn("generateProto")
//     task.outputs.upToDateWhen { false }
}
