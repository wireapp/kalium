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
    id("com.github.johnrengelman.shadow") version "7.1.2"
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

// Configure mainClass using the new Kotlin JVM DSL
kotlin {
    jvmToolchain(17)
}

// Configure the jar task with main class
tasks.jar {
    manifest {
        attributes["Main-Class"] = mainFunctionClassName
    }
}

// Create a custom run task since we're not using the application plugin
tasks.register<JavaExec>("run") {
    mainClass.set(mainFunctionClassName)
    classpath = sourceSets["main"].runtimeClasspath
    jvmArgs = listOf("-Djava.library.path=/usr/local/lib/:../native/libs")
    args = listOf("server", "config.yml")
    isIgnoreExitValue = true
    standardInput = System.`in`
    standardOutput = System.out
}

tasks.shadowJar {
    archiveBaseName.set("testservice")
    mergeServiceFiles()
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

    implementation(projects.network) {
        exclude("org.slf4j", "slf4j-api")
    }
    implementation(projects.cryptography) {
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
