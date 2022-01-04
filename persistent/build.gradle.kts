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
        val commonMain by getting {
            dependencies {
                // coroutines
                implementation(Dependencies.Coroutines.core)
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.0")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                // coroutines
                implementation(Dependencies.Coroutines.test)
            }
        }
        val jvmMain by getting
        val jvmTest by getting
        val androidMain by getting {
            dependencies {
                implementation ("androidx.datastore:datastore-preferences:1.0.0")

            }
        }
        val androidTest by getting
    }
}
