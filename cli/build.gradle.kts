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

import com.wire.kalium.plugins.commonDokkaConfig
import com.wire.kalium.plugins.commonJvmConfig

@Suppress("DSL_SCOPE_VIOLATION")

plugins {
    application
    kotlin("multiplatform")
}
val mainFunctionClassName = "com.wire.kalium.cli.MainKt"

application {
    mainClass.set(mainFunctionClassName)
}

tasks.jar {
    manifest.attributes["Main-Class"] = mainFunctionClassName
    val dependencies = configurations
        .runtimeClasspath
        .get()
        .map(::zipTree)
    from(dependencies)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

kotlin {
    applyDefaultHierarchyTemplate()
    val jvmTarget = jvm {
        commonJvmConfig(includeNativeInterop = false)
        tasks.named("run", JavaExec::class) {
            isIgnoreExitValue = true
            standardInput = System.`in`
            standardOutput = System.out
        }
    }
    macosX64 {
        binaries {
            executable()
        }
    }
    macosArm64 {
        binaries {
            executable()
        }
    }

    sourceSets {
        val commonMain by sourceSets.getting {
            dependencies {
                implementation(project(":network"))
                implementation(project(":cryptography"))
                implementation(project(":logic"))
                implementation(project(":util"))

                implementation(libs.cliKt)
                implementation(libs.ktor.utils)
                implementation(libs.coroutines.core)
                implementation(libs.ktxDateTime)
                implementation(libs.mordant)
                implementation(libs.ktxSerialization)
                implementation(libs.ktxIO)
            }
        }
        val jvmMain by getting {
             dependencies {
                 implementation(libs.ktor.okHttp)
                 implementation(libs.okhttp.loggingInterceptor)
             }
        }
        val macosMain by getting {
            dependencies {
                implementation(libs.ktor.iosHttp)
            }
        }

        tasks.withType<JavaExec> {
            // code to make run task in kotlin multiplatform work
            val compilation = jvmTarget.compilations.getByName<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmCompilation>("main")

            val classes = files(
                compilation.runtimeDependencyFiles,
                compilation.output.allOutputs
            )
            classpath(classes)
            setJvmArgs(listOf("-Djava.library.path=/usr/local/lib/:./native/libs"))
        }
    }
}

commonDokkaConfig()
