import com.android.build.gradle.internal.tasks.factory.dependsOn

buildscript {
    val kotlinVersion = "1.6.10"
    val sqlDelightVersion = "2.0.0-SNAPSHOT"
    val dokkaVersion = "1.6.10"

    repositories {
        google()
        mavenCentral()
        maven(url = "https://oss.sonatype.org/content/repositories/snapshots")
    }

    dependencies {
        // keeping this here to allow AS to automatically update
        classpath("com.android.tools.build:gradle:7.0.4")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
        classpath("app.cash.sqldelight:gradle-plugin:$sqlDelightVersion")
        classpath("org.jetbrains.dokka:dokka-gradle-plugin:$dokkaVersion")
    }
}

repositories {
    google()
    mavenCentral()
}

plugins {
    id("org.jetbrains.dokka") version "1.6.10"
}

dependencies {
    dokkaHtmlPlugin("org.jetbrains.dokka:kotlin-as-java-plugin:1.6.10")
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
//tasks.dokkaHtmlMultiModule.configure {
//    outputDirectory.set(buildDir.resolve("dokkaCustomMultiModuleOutput"))
//}

apply(rootProject.file("gradle/dokka.gradle"))
