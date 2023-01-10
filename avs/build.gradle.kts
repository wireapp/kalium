// DISCLAIMER: This file should be removed when the avs dependency is on the proper artifactory.
plugins {
    id("maven-publish")
}

repositories {
    mavenLocal()
    mavenCentral()
    maven(file("libs"))
}

project.afterEvaluate {
    publishing {
        publications {
            group = "com.wire"
            version = "9.0.2-rc1"

            create<MavenPublication>("maven") {
                artifactId = "avs"
                artifact(file("avs-9-0-2.aar"))
            }
        }
    }
}
