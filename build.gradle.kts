buildscript {
    val kotlinVersion = "1.6.10"
    val sqlDelightVersion = "2.0.0-SNAPSHOT"

    repositories {
        google()
        mavenCentral()
        maven(url = "https://oss.sonatype.org/content/repositories/snapshots")
    }

    dependencies {
        // keeping this here to allow AS to automatically update
        classpath("com.android.tools.build:gradle:7.0.3")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
        classpath("app.cash.sqldelight:gradle-plugin:$sqlDelightVersion")
        classpath("org.jetbrains.dokka:dokka-gradle-plugin:1.6.10")
    }
}

repositories {
    google()
    mavenCentral()
}

tasks.withType<Test> {
    useJUnitPlatform {
        reports.junitXml.required.set(true)
        jvmArgs = jvmArgs?.plus(listOf("-Djava.library.path=/usr/local/lib/:/Users/ymedina/projects/wire/kalium/native/libs"))
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven(url = "https://oss.sonatype.org/content/repositories/snapshots")
        if (!listOf("android", "cli").contains(name)) {
            apply(plugin = "org.jetbrains.dokka")
        }
    }
}

rootProject.plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin> {
    rootProject.the<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension>().nodeVersion = "16.0.0"
}
