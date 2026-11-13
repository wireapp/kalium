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

import org.jetbrains.dokka.gradle.DokkaExtension

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    id(libs.plugins.kalium.library.get().pluginId)
}

kaliumLibrary {
    multiplatform {
        enableJsTests.set(false)
    }
}

val generatedKotlinSources by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}
dependencies {
    generatedKotlinSources(project(path = ":tools:protobuf-codegen", configuration = "generatedKotlinSources"))
}

val prepareProtobufSources by tasks.registering(PrepareProtobufSourcesTask::class) {
    sourceDirectory.set(layout.dir(generatedKotlinSources.elements.map { it.single().asFile }))
    outputDirectory.set(layout.buildDirectory.dir("generated/protobuf/commonMain/kotlin"))
    fileAnnotation.set("""@file:Suppress("UNNECESSARY_NOT_NULL_ASSERTION")""")
}

extensions.configure<DokkaExtension> {
    dokkaSourceSets.named("commonMain") {
        // DokkaCompatibility supplies source files through a provider, so retain their producer dependency.
        sourceRoots.builtBy(prepareProtobufSources)
    }
}

kotlin {
    sourceSets {
        val commonMain by getting {
            kotlin.srcDir(prepareProtobufSources)
            dependencies {
                api(libs.pbandk.runtime.common)
            }
        }
        val commonTest by getting {
            dependencies { }
        }
        val androidDeviceTest by getting {
            dependencies {
                implementation(libs.androidtest.runner)
                implementation(libs.androidtest.rules)
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

        val macosArm64Main by getting {
            dependencies {
                api(libs.pbandk.runtime.macArm64)
            }
        }
    }
}
