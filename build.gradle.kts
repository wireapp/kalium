import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.github.leandroborgesferreira.dagcommand.DagCommandPlugin
import com.github.leandroborgesferreira.dagcommand.extension.CommandExtension

buildscript {
    repositories {
        google()
        mavenCentral()
        maven(url = "https://raw.githubusercontent.com/wireapp/wire-maven/main/releases")
    }

    dependencies {
        // keeping this here to allow AS to automatically update
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
    google()
    mavenCentral()
}

plugins {
    val dokkaVersion = "1.6.10"
    id("org.jetbrains.dokka") version "$dokkaVersion"
    id("org.jetbrains.kotlinx.kover") version "0.5.1"
    id("scripts.testing")
    id("com.louiscad.complete-kotlin") version "1.1.0"
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
        mavenLocal()
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
