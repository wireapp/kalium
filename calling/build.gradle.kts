plugins {
    Plugins.androidLibrary(this)
    Plugins.multiplatform(this)
}

group = "com.wire.kalium"
version = "0.0.1-SNAPSHOT"

android {
    compileSdk = Android.Sdk.compile
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    defaultConfig {
        minSdk = Android.Sdk.min
        targetSdk = Android.Sdk.target
        consumerProguardFiles("consumer-proguard-rules.pro")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "1.8"
            kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
        }
        testRuns["test"].executionTask.configure {
            useJUnit()
        }
    }
    android()

    sourceSets {
        val androidTest by getting {
            dependencies {
                implementation(libs.androidTestRunner)
                implementation(libs.androidTestRules)
            }
        }
        val androidMain by getting {
            dependencies {
                api(libs.jna)
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
