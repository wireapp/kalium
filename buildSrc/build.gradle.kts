plugins {
    `kotlin-dsl`
    `kotlin-dsl-precompiled-script-plugins`
}

repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation(libs.kotlin.plugin)
    implementation("com.android.tools.build:gradle:${libs.versions.agp.get()}")
}

gradlePlugin {
    plugins {
        val libraryId = libs.plugins.kalium.library.get().pluginId
        register(libraryId) {
            id = libraryId
            implementationClass = "com.wire.kalium.plugins.LibraryPlugin"
        }
    }
}
