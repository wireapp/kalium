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

import io.github.leandroborgesferreira.dagcommand.DagCommandPlugin
import io.github.leandroborgesferreira.dagcommand.extension.CommandExtension
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnLockMismatchReport
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension

buildscript {
    repositories {
        google()
        mavenCentral()
    }

    dependencies {
        classpath("com.android.tools.build:gradle:${libs.versions.agp.get()}")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
        classpath("app.cash.sqldelight:gradle-plugin:${libs.versions.sqldelight.get()}")
        classpath("org.jetbrains.dokka:dokka-gradle-plugin:${libs.versions.dokka.get()}")
        classpath("com.google.protobuf:protobuf-gradle-plugin:${libs.versions.protobufCodegen.get()}")
        classpath("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:${libs.versions.detekt.get()}")
        classpath("io.gitlab.arturbosch.detekt:detekt-cli:${libs.versions.detekt.get()}")
        classpath("io.github.leandroborgesferreira:dag-command:${libs.versions.dagCommand.get()}")
    }
}

repositories {
    wireDetektRulesRepo()
    google()
    mavenCentral()
    maven(url = "https://oss.sonatype.org/content/repositories/snapshots")
}

plugins {
    id("org.jetbrains.dokka")
    alias(libs.plugins.kover)
    id("scripts.testing")
    id("scripts.detekt")
    alias(libs.plugins.moduleGraph)
    alias(libs.plugins.completeKotlin)
    alias(libs.plugins.compose.compiler) apply false
}

dependencies {
    dokkaHtmlPlugin("org.jetbrains.dokka:kotlin-as-java-plugin:${libs.versions.dokka.get()}")
}

tasks.withType<Test> {
    useJUnitPlatform {
        reports.junitXml.required.set(true)
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

apply<DagCommandPlugin>()
the<CommandExtension>().run {
    filter = "all"
    defaultBranch = "origin/develop"
    outputType = "json"
    printModulesInfo = true
}

kover {
    useJacoco()
}
koverReport {
    filters {
        includes {
            packages("com.wire.kalium")
        }
    }
}

fun Project.configureKover() {
    pluginManager.apply("org.jetbrains.kotlinx.kover")

    kover {
        useJacoco()
    }
    koverReport {
        filters {
            includes {
                packages("com.wire.kalium")
            }
        }
    }
}

subprojects {
    // We only want coverage reports of actual Kalium
    // Samples and other side-projects can have their own rules
    if (name in setOf("monkeys", "testservice", "cli", "android")) {
        return@subprojects
    }
    configureKover()
}

rootProject.plugins.withType(org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin::class.java) {
    // For unknown reasons, yarn.lock checks are failing on Github Actions
    // Considering JS support is quite experimental for us, we can live with this for now
    rootProject.the<YarnRootExtension>().yarnLockMismatchReport =
        YarnLockMismatchReport.WARNING
}

dependencies {
    kover(project(":logic"))
    kover(project(":cryptography"))
    kover(project(":util"))
    kover(project(":network"))
    kover(project(":network-util"))
    kover(project(":persistence"))
    kover(project(":logger"))
    kover(project(":calling"))
    kover(project(":protobuf"))
}

rootProject.plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin> {
    rootProject.the<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension>().nodeVersion = "17.6.0"
}

tasks.dokkaHtmlMultiModule.configure {}

moduleGraphConfig {
    readmePath.set("./README.md")
    heading.set("#### Dependency Graph")
}
