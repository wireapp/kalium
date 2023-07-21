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

import com.github.leandroborgesferreira.dagcommand.DagCommandPlugin
import com.github.leandroborgesferreira.dagcommand.extension.CommandExtension
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnLockMismatchReport
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension

buildscript {
    repositories {
        google()
        mavenCentral()
        maven(url = "https://raw.githubusercontent.com/wireapp/wire-maven/main/releases")
    }

    dependencies {
        classpath("com.android.tools.build:gradle:${libs.versions.agp.get()}")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
        classpath("app.cash.sqldelight:gradle-plugin:${libs.versions.sqldelight.get()}")
        classpath("com.wire:carthage-gradle-plugin:${libs.versions.carthage.get()}")
        classpath("org.jetbrains.dokka:dokka-gradle-plugin:${libs.versions.dokka.get()}")
        classpath("com.google.protobuf:protobuf-gradle-plugin:${libs.versions.protobufCodegen.get()}")
        classpath("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:${libs.versions.detekt.get()}")
        classpath("io.gitlab.arturbosch.detekt:detekt-cli:${libs.versions.detekt.get()}")
        classpath("com.github.leandroborgesferreira:dag-command:1.5.3")
    }
}

repositories {
    mavenLocal()
    wireDetektRulesRepo()
    google()
    mavenCentral()
}

plugins {
    id("org.jetbrains.dokka")
    alias(libs.plugins.kover)
    id("scripts.testing")
    id("scripts.detekt")
    alias(libs.plugins.completeKotlin)
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
        mavenLocal()
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/wireapp/core-crypto")
            credentials {
                username = getLocalProperty("github.package_registry.user", System.getenv("GITHUB_USER"))
                password = getLocalProperty("github.package_registry.token", System.getenv("GITHUB_TOKEN"))
            }
        }
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

subprojects {
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
    kover(project(":persistence"))
    kover(project(":logger"))
    kover(project(":calling"))
    kover(project(":protobuf"))
}

rootProject.plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin> {
    rootProject.the<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension>().nodeVersion = "17.6.0"
}

tasks.dokkaHtmlMultiModule.configure {}
