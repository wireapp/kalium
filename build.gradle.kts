import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.github.leandroborgesferreira.dagcommand.DagCommandPlugin
import com.github.leandroborgesferreira.dagcommand.extension.CommandExtension

buildscript {
    val kotlinVersion = "1.7.10"
    val dokkaVersion = "1.7.10"
    val sqlDelightVersion = "2.0.0-alpha01"
    val protobufCodegenVersion = "0.8.19"
    val carthageVersion = "0.0.1"
    val detektVersion = "1.19.0"

    repositories {
        google()
        mavenCentral()
        maven(url = "https://raw.githubusercontent.com/wireapp/wire-maven/main/releases")
    }

    dependencies {
        // keeping this here to allow AS to automatically update
        classpath("com.android.tools.build:gradle:7.2.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
        classpath("app.cash.sqldelight:gradle-plugin:$sqlDelightVersion")
        classpath("com.wire:carthage-gradle-plugin:$carthageVersion")
        classpath("org.jetbrains.dokka:dokka-gradle-plugin:$dokkaVersion")
        classpath("com.google.protobuf:protobuf-gradle-plugin:$protobufCodegenVersion")
        classpath("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:$detektVersion")
        classpath("io.gitlab.arturbosch.detekt:detekt-cli:$detektVersion")
        classpath("com.github.leandroborgesferreira:dag-command:1.5.3")
    }
}

repositories {
    google()
    mavenCentral()
}

plugins {
    id("org.jetbrains.dokka") version "1.7.10"
    id("org.jetbrains.kotlinx.kover") version "0.5.1"
    id("scripts.testing")
}

dependencies {
    dokkaHtmlPlugin("org.jetbrains.dokka:kotlin-as-java-plugin:1.7.10")
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
        mavenLocal()
        maven(url = "https://raw.githubusercontent.com/wireapp/wire-maven/main/releases")
    }
}

apply<DagCommandPlugin>()
the<CommandExtension>().run {
    filter = "all"
    defaultBranch = "origin/develop"
    outputType = "json"
    printModulesInfo = true
}

subprojects {
    this.tasks.withType<Test> {
        if (name != "jvmTest" && name != "jsTest") {
            the<kotlinx.kover.api.KoverTaskExtension>().apply {
                isDisabled = true
            }
        } else {
            the<kotlinx.kover.api.KoverTaskExtension>().apply {
                includes = listOf("com.wire.kalium.*")
            }
        }
    }
}

kover {
    coverageEngine.set(kotlinx.kover.api.CoverageEngine.JACOCO)
}

rootProject.plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin> {
    rootProject.the<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension>().nodeVersion = "17.6.0"
}

tasks.create("dokkaClean") {
    group = "documentation"
    project.delete(file("build/dokka"))
}

tasks.dokkaHtml.dependsOn(tasks.dokkaHtmlMultiModule)
tasks.dokkaHtmlMultiModule.dependsOn(tasks.getByName("dokkaClean"))

apply(from = "$rootDir/gradle/detekt.gradle")
apply(from = "$rootDir/gradle/dokka.gradle")
