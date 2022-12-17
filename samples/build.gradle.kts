@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    id(libs.plugins.android.library.get().pluginId)
    id(libs.plugins.kotlin.multiplatform.get().pluginId)
    id(libs.plugins.kalium.library.get().pluginId)
}

kaliumLibrary {
    multiplatform {
        enableJs.set(false)
    }
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":network"))
                implementation(project(":cryptography"))
                implementation(project(":persistence"))
                implementation(project(":protobuf"))
                implementation(project(":logger"))

                implementation(libs.coroutines.core)
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(project(":logic"))
                implementation(project(":calling"))
            }
        }

        val androidMain by getting {
            dependencies {
                implementation(project(":logic"))
                implementation(project(":calling"))
            }
        }
    }
}
