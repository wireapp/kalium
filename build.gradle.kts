
buildscript {
    val kotlinVersion = "1.6.0"
    val sqlDelightVersion = "1.5.3"

    repositories {
        google()
        mavenCentral()
    }

    dependencies {
        // keeping this here to allow AS to automatically update
        classpath("com.android.tools.build:gradle:7.0.3")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${kotlinVersion}")
        classpath("com.squareup.sqldelight:gradle-plugin:$sqlDelightVersion")
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
        google()
        mavenCentral()
    }
}
