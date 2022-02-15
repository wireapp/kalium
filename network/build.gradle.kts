plugins {
    Plugins.androidLibrary(this)
    Plugins.multiplatform(this)
    Plugins.serialization(this)
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
                implementation(Dependencies.Coroutines.core) {
                    version {
                        strictly(Versions.coroutines)
                    }
                }

                // ktor
                api(Dependencies.Ktor.core)
                implementation(Dependencies.Ktor.utils)
                implementation(Dependencies.Ktor.json)
                implementation(Dependencies.Ktor.serialization)
                implementation(Dependencies.Ktor.logging)
                implementation(Dependencies.Ktor.authClient)
                implementation(Dependencies.Ktor.webSocket)
                implementation(Dependencies.Ktor.contentNegotiation)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                // coroutines
                implementation(Dependencies.Coroutines.test)
                // ktor test
                implementation(Dependencies.Ktor.mock)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(Dependencies.Ktor.okHttp)
            }
        }
        val jvmTest by getting
        val androidMain by getting {
            dependencies {
                implementation(Dependencies.Ktor.okHttp)
            }
        }
        val androidTest by getting
    }
}
