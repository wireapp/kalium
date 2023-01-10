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
    implementation("org.jetbrains.dokka:dokka-gradle-plugin:${libs.versions.dokka.get()}")
    implementation("com.android.tools.build:gradle:${libs.versions.agp.get()}")
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:${libs.versions.detekt.get()}")
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
