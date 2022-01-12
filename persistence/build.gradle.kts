plugins {
    Plugins.androidLibrary(this)
    Plugins.multiplatform(this)
    Plugins.sqlDelight(this)
}

group = "com.wire.kalium"
version = "0.0.1-SNAPSHOT"

sqldelight {
    database("AppDatabase") {
        packageName = "com.wire.kalium.persistence.db"
    }
}

android {
    compileSdk = Android.Sdk.compile
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    defaultConfig {
        minSdk = Android.Sdk.min
        targetSdk = Android.Sdk.target
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
    js(IR) {
        browser {
            testTask {
                useMocha {
                    timeout = "5s"
                }
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // coroutines
                implementation(Dependencies.Coroutines.core)
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.0")
                implementation(Dependencies.SqlDelight.runtime)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                // coroutines
                implementation(Dependencies.Coroutines.test)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(Dependencies.SqlDelight.jvmDriver)
            }
        }
        val jvmTest by getting
        val jsMain by getting {
            dependencies {
                implementation(Dependencies.SqlDelight.jsDriver)
                implementation(devNpm("copy-webpack-plugin", "9.1.0"))
            }
        }
        val jsTest by getting
        val androidMain by getting {
            dependencies {
                implementation ("androidx.datastore:datastore-preferences:1.0.0")
                implementation(Dependencies.SqlDelight.androidDriver)
            }
        }
        val androidTest by getting {
            dependencies {
                implementation (Dependencies.Coroutines.test)
                implementation(Dependencies.AndroidInstruments.androidxArchTesting)
            }
        }
        val androidAndroidTest by getting {
            dependencies {
                implementation(Dependencies.AndroidInstruments.androidTestRunner)
                implementation(Dependencies.AndroidInstruments.androidTestRules)
                implementation(Dependencies.AndroidInstruments.androidTestCore)
            }
        }
    }
}
