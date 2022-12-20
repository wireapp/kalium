@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    id(libs.plugins.android.library.get().pluginId)
    id(libs.plugins.kotlin.multiplatform.get().pluginId)
    id(libs.plugins.kalium.library.get().pluginId)
}

kaliumLibrary {
    multiplatform()
}

kotlin {

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":persistence"))
                implementation(kotlin("test"))
                // coroutines
                implementation(libs.coroutines.core)
                implementation(libs.coroutines.test)
                implementation(libs.settings.kmpTest)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.androidtest.runner)
                implementation(libs.androidtest.rules)
                implementation(libs.androidtest.core)
            }
        }
    }
}
