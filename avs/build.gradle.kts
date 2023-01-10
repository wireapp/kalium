// DISCLAIMER: Delete this whole "avs" directory when the publication is available at the proper artifactory.
rootProject.gradle.afterProject {
    allprojects {
        repositories {
            val avsLocal = maven(url = uri("$rootDir/avs/localrepo/"))
            exclusiveContent {
                forRepositories(avsLocal)
                filter {
                    includeModule("com.wire", "avs")
                }
            }
        }
    }
}
