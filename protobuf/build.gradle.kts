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

import com.google.protobuf.gradle.GenerateProtoTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompileTool

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

val compileTasks = tasks.matching { it is KotlinCompileTool }

codegenProject.tasks
    .matching { it.name == "generateProto" }
    .all {
        this as GenerateProtoTask
        compileTasks.forEach { compileTask ->
            compileTask.dependsOn(this)
        }
        // Always generate protobuf files. So we make sure they exist.
        outputs.upToDateWhen {
            false
        }
        doLast {
            outputSourceDirectorySet.srcDirs.forEach { generatedDirectory ->
                generatedFilesBaseDir.mkdirs()
                val targetDirectory = File(generatedFilesBaseDir, generatedDirectory.name)
                // Delete already existing files
                targetDirectory.deleteRecursively()

                // Move generated files to target directory
                val movingSucceeded = generatedDirectory.renameTo(targetDirectory)

                require(movingSucceeded) {
                    "Failed to move Generated protobuf files from '${generatedDirectory.absolutePath}' " +
                            "to destination directory '${targetDirectory.absolutePath}'"
                }
            }
        }
    }
