@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    id(libs.plugins.android.library.get().pluginId)
    id(libs.plugins.kotlin.multiplatform.get().pluginId)
    id(libs.plugins.kalium.library.get().pluginId)
}

kaliumLibrary {
    multiplatform {
        enableiOS.set(false)
        enableJs.set(false)
    }
}

kotlin {

    sourceSets {
        val androidTest by getting {
            dependencies {
                implementation(libs.androidtest.runner)
                implementation(libs.androidtest.rules)
            }
        }
        val androidMain by getting {
            dependencies {
                api(project(":avs")) // TODO temporary until avs9.0.2 is available on Maven-central
                api(libs.jna.map {
                    project.dependencies.create(it, closureOf<ExternalModuleDependency> {
                        artifact {
                            type = "aar"
                        }
                    })
                })
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.jna)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
