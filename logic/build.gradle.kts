import Plugins.ksp

plugins {
    Plugins.androidLibrary(this)
    Plugins.multiplatform(this)
    Plugins.serialization(this)
    Plugins.ksp(this)
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
        }
        testRuns["test"].executionTask.configure {
            useJUnit()
        }
    }
    android()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":network"))
                implementation(project(":cryptography"))
                implementation(project(":persistence"))

                // coroutines
                implementation(Dependencies.Coroutines.core)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                // coroutines
                implementation(Dependencies.Coroutines.test)

                // mocking
                implementation(Dependencies.Test.mockative)
            }
        }
        val jvmMain by getting {
            dependencies { }
        }
        val jvmTest by getting
        val androidMain by getting {
            dependencies {
                implementation(Dependencies.Android.dataStorePreferences)
            }
        }
        val androidTest by getting
    }
}

dependencies {
    ksp(Dependencies.Test.mockativeProcessor)
}
