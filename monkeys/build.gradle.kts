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

@Suppress("DSL_SCOPE_VIOLATION")

plugins {
    kotlin("jvm")
    id(libs.plugins.sqldelight.get().pluginId)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.mockative)
}
val mainFunctionClassName = "com.wire.kalium.monkeys.MainKt"
val replayerMainFunctionClassName = "com.wire.kalium.monkeys.ReplayerKt"
val monkeyMainFunctionClassName = "com.wire.kalium.monkeys.MonkeyKt"

kotlin {
    jvmToolchain(17)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(JavaVersion.VERSION_17.majorVersion))
    }
}

// Create custom run task for the main entry point
tasks.register<JavaExec>("run") {
    mainClass.set(mainFunctionClassName)
    classpath = sourceSets["main"].runtimeClasspath
}

// Create custom run tasks for other entry points
tasks.register<JavaExec>("runReplayer") {
    mainClass.set(replayerMainFunctionClassName)
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("runServer") {
    mainClass.set(monkeyMainFunctionClassName)
    classpath = sourceSets["main"].runtimeClasspath
}

// Create main startScripts task
val startScripts by tasks.register("startScripts", CreateStartScripts::class) {
    mainClass.set(mainFunctionClassName)
    outputDir = file("${buildDir}/scripts")
    classpath = files(tasks.jar)
    applicationName = "monkeys"
}

val replayerScripts by tasks.register("replayerScripts", CreateStartScripts::class) {
    mainClass.set(replayerMainFunctionClassName)
    outputDir = startScripts.outputDir
    classpath = startScripts.classpath
    applicationName = "replayer"
}

val serverScripts by tasks.register("serverScripts", CreateStartScripts::class) {
    mainClass.set(monkeyMainFunctionClassName)
    outputDir = startScripts.outputDir
    classpath = startScripts.classpath
    applicationName = "monkey-server"
}

tasks.named<CreateStartScripts>("startScripts") {
    dependsOn(replayerScripts)
    dependsOn(serverScripts)
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = mainFunctionClassName
        attributes["CC-Version"] = libs.coreCryptoJvm.get().version
    }
}

sourceSets {
    val main by getting {
        dependencies {
            implementation(projects.network)
            implementation(projects.cryptography)
            implementation(projects.logic)
            implementation(projects.util)

            implementation(libs.cliKt)
            implementation(libs.ktor.utils)
            implementation(libs.coroutines.core)
            implementation(libs.ktxDateTime)
            implementation(libs.ktxReactive)

            implementation(libs.ktxSerialization)
            implementation(libs.ktor.serialization)
            implementation(libs.ktor.okHttp)
            implementation(libs.ktor.contentNegotiation)
            implementation(libs.ktor.json)
            implementation(libs.ktor.authClient)
            implementation(libs.ktor.server)
            implementation(libs.ktor.serverNetty)
            implementation(libs.ktor.serverLogging)
            implementation(libs.ktor.serverCallId)
            implementation(libs.ktor.serverMetrics)
            implementation(libs.ktor.serverContentNegotiation)
            implementation(libs.ktor.statusPages)
            implementation(libs.okhttp.loggingInterceptor)
            implementation(libs.micrometer)
            implementation(libs.slf4js)

            implementation(libs.faker)

            implementation(libs.concurrentCollections)
            implementation(libs.statelyCommons)

            implementation(libs.sqldelight.r2dbc)
            implementation(libs.sqldelight.async)
            implementation(libs.r2dbc.postgres)
            implementation(libs.r2dbc.spi)
        }
    }

    val test by getting {
        dependencies {
            implementation(libs.kotlin.test)

            // coroutines
            implementation(libs.coroutines.test)
            implementation(libs.turbine)

            // mocking
            implementation(libs.okio.test)
            implementation(libs.settings.kmpTest)
            implementation(libs.mockk)
        }
    }

    tasks.withType<JavaExec> {
        jvmArgs = listOf("-Djava.library.path=/usr/local/lib/:./native/libs")
    }
}

sqldelight {
    databases {
        create("InfiniteMonkeysDB") {
            dialect(libs.sqldelight.postgres.get().toString())
            packageName.set("com.wire.kalium.monkeys.db")
            generateAsync.set(true)
            srcDirs.setFrom("src/main/db_monkeys")
        }
    }
}

commonDokkaConfig()
