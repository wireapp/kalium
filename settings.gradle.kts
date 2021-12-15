rootProject.name = "Kalium"

// Assume that all folders that contain a build.gradle.kts and are not buildSrc should be included
rootDir
    .walk()
    .maxDepth(1)
    .filter {
        it.name != "buildSrc" && it.isDirectory &&
                file("${it.absolutePath}/build.gradle.kts").exists()
    }.forEach {
        include(":${it.name}")
    }
include("plugin")
