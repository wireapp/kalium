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
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${kotlinVersion}")
        classpath("app.cash.sqldelight:gradle-plugin:$sqlDelightVersion")
    }
}

repositories {
    google()
    mavenCentral()
}

tasks.withType<Test> {
    useJUnitPlatform {
        reports.junitXml.required.set(true)
    }
}

allprojects {
    repositories {
        maven(url = "https://oss.sonatype.org/content/repositories/snapshots")
        google()
        mavenCentral()
    }
}
