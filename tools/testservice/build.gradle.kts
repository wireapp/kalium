import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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

plugins {
    kotlin("jvm")
    java
    application
    id("com.gradleup.shadow") version "9.3.1"
}

group = "com.wire.kalium.testservice"
version = "0.0.1-SNAPSHOT"

object Versions {
    const val dropwizard = "2.1.4"
    const val prometheus_simpleclient = "0.1.0"
}

val mainFunctionClassName = "com.wire.kalium.testservice.TestserviceApplication"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(JavaVersion.VERSION_17.majorVersion))
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

application {
    mainClass.set(mainFunctionClassName)
}

tasks.named("run", JavaExec::class) {
    jvmArgs = listOf("-Djava.library.path=/usr/local/lib/:../native/libs")
    args = listOf("server", "config.yml")
    isIgnoreExitValue = true
    standardInput = System.`in`
    standardOutput = System.out
}

tasks.shadowJar {
    archiveBaseName.set("testservice")

    // fix: Allow duplicates by default so mergeServiceFiles() can combine entries
    // from multiple dependencies (fixes Dropwizard service discovery in shadow 9.3.1)
    duplicatesStrategy = DuplicatesStrategy.INCLUDE

    mergeServiceFiles()

    exclude("META-INF/*.RSA")
    exclude("META-INF/*.SF")
    exclude("META-INF/MANIFEST.MF")

    manifest {
        attributes(mapOf("Main-Class" to mainFunctionClassName))
    }
}

dependencies {
    add("implementation", "io.dropwizard:dropwizard-core:${Versions.dropwizard}")
    add("implementation", "com.smoketurner:dropwizard-swagger:2.1.4-1")
    add("implementation", "org.slf4j:slf4j-api:1.7.22")

    // prometheus metrics
    add("implementation", "io.prometheus:simpleclient_dropwizard:${Versions.prometheus_simpleclient}")
    add("implementation", "io.prometheus:simpleclient_servlet:${Versions.prometheus_simpleclient}")

    implementation(projects.data.network) {
        exclude("org.slf4j", "slf4j-api")
    }
    implementation(projects.core.cryptography) {
        exclude("org.slf4j", "slf4j-api")
    }
    implementation(projects.logic) {
        exclude("org.slf4j", "slf4j-api")
    }

    // Okio
    implementation(libs.okio.core)

    // Test
    testImplementation(libs.kotlin.test)
}
