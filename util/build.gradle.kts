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
                // coroutines
                implementation(libs.coroutines.core)
                implementation(libs.ktxDateTime)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.coroutines.test)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.annotation)
            }
        }
    }
}
