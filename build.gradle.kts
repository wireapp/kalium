import com.android.build.gradle.internal.tasks.factory.dependsOn

buildscript {
    val kotlinVersion = "1.6.10"
    val dokkaVersion = "1.6.10"
    val sqlDelightVersion = "2.0.0-SNAPSHOT"

    repositories {
        google()
        mavenCentral()
        maven(url = "https://oss.sonatype.org/content/repositories/snapshots")
        maven(url = "https://raw.githubusercontent.com/wireapp/wire-maven/main/releases")
    }

    dependencies {
        // keeping this here to allow AS to automatically update
        classpath("com.android.tools.build:gradle:7.0.4")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
        classpath("app.cash.sqldelight:gradle-plugin:$sqlDelightVersion")
        classpath("com.wire:carthage-gradle-plugin:0.0.1")
        classpath("org.jetbrains.dokka:dokka-gradle-plugin:$dokkaVersion")
    }
}

repositories {
    google()
    mavenCentral()
}

plugins {
    val dokkaVersion = "1.6.10"
    id("org.jetbrains.dokka") version "$dokkaVersion"
}

dependencies {
    val dokkaVersion = "1.6.10"
    dokkaHtmlPlugin("org.jetbrains.dokka:kotlin-as-java-plugin:$dokkaVersion")
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
        maven(url = "https://oss.sonatype.org/content/repositories/snapshots")
    }
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

apply(rootProject.file("gradle/dokka.gradle"))
