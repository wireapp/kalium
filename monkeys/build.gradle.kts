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
    application
    kotlin("jvm")
    id(libs.plugins.sqldelight.get().pluginId)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}
val mainFunctionClassName = "com.wire.kalium.monkeys.MainKt"
val replayerMainFunctionClassName = "com.wire.kalium.monkeys.ReplayerKt"
val monkeyMainFunctionClassName = "com.wire.kalium.monkeys.MonkeyKt"

application {
    mainClass.set(mainFunctionClassName)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(JavaVersion.VERSION_17.majorVersion))
    }
}

val replayerScripts by tasks.register("replayerScripts", CreateStartScripts::class) {
    mainClass.set(replayerMainFunctionClassName)
    outputDir = tasks.startScripts.get().outputDir
    classpath = tasks.startScripts.get().classpath
    applicationName = "replayer"
}

val serverScripts by tasks.register("serverScripts", CreateStartScripts::class) {
    mainClass.set(monkeyMainFunctionClassName)
    outputDir = tasks.startScripts.get().outputDir
    classpath = tasks.startScripts.get().classpath
    applicationName = "monkey-server"
}

tasks.startScripts {
    dependsOn(replayerScripts)
    dependsOn(serverScripts)
}

tasks.jar {
    manifest {
        attributes["CC-Version"] = libs.coreCrypto.get().version
    }
}

sourceSets {
    val main by getting {
        dependencies {
            implementation(project(":network"))
            implementation(project(":cryptography"))
            implementation(project(":logic"))
            implementation(project(":util"))

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
            implementation(kotlin("test"))

            // coroutines
            implementation(libs.coroutines.test)
            implementation(libs.turbine)

            // mocking
            implementation(libs.mockative.runtime)
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
