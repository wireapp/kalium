/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

plugins {
    kotlin("jvm")
    application
}
val mainFunctionClassName = "com.wire.kalium.cli.CLIApplicationKt"

application {
    mainClass.set(mainFunctionClassName)
}

tasks.named("run", JavaExec::class) {
    isIgnoreExitValue = true
    standardInput = System.`in`
    standardOutput = System.out
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

commonDokkaConfig()

dependencies {
    implementation(project(":network"))
    implementation(project(":cryptography"))
    implementation(project(":logic"))
    implementation(project(":util"))

    implementation(libs.cliKt)
    implementation(libs.ktor.utils)
    implementation(libs.ktor.okHttp)
    implementation(libs.okhttp.loggingInterceptor)
    implementation(libs.coroutines.core)
    implementation(libs.ktxDateTime)
}
