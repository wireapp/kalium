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

        val darwinMain by creating {
            dependsOn(commonMain)
        }
        val darwinTest by creating {
            dependsOn(commonTest)
        }
        val iosX64Main by getting {
            dependsOn(darwinMain)
        }
        val iosX64Test by getting {
            dependsOn(darwinTest)
        }
        val iosArm64Main by getting {
            dependsOn(darwinMain)
        }
        val iosArm64Test by getting {
            dependsOn(darwinTest)
        }
        val iosSimulatorArm64Main by getting {
            dependsOn(darwinMain)
        }
        val iosSimulatorArm64Test by getting {
            dependsOn(darwinTest)
        }
        val macosX64Main by getting {
            dependsOn(darwinMain)
        }
        val macosX64Test by getting {
            dependsOn(darwinTest)
        }
    }
}
