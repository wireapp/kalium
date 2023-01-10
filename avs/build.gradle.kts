// DISCLAIMER: Delete this whole "avs" directory when the publication is available at the proper artifactory.
rootProject.gradle.afterProject {
    allprojects {
        repositories {
            maven {
                url = uri("$rootDir/avs/localrepo/")
            }
        }
    }
}
