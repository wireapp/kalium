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
        consumerProguardFiles("consumer-proguard-rules.pro")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    packagingOptions {
        resources.pickFirsts.add("google/protobuf/*.proto")
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
                implementation(project(":protobuf"))
                api(project(":logger"))
                api(project(":calling"))
                implementation(project(":util"))

                // coroutines
                implementation(Dependencies.Coroutines.core) {
                    version {
                        strictly(Versions.coroutines)
                    }
                }
                implementation(Dependencies.Coroutines.core)
                implementation(Dependencies.Kotlinx.serialization)
                implementation(Dependencies.Kotlinx.dateTime)
                implementation(Dependencies.UUID.benAsherUUID)
                // the Dependency is duplicated between here and persistence build.gradle.kts
                implementation(Dependencies.MultiplatformSettings.settings)

                // Okio
                implementation(Dependencies.Okio.core)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                // coroutines
                implementation(Dependencies.Coroutines.test)
                implementation(Dependencies.Test.turbine)

                // mocking
                implementation(Dependencies.Test.mockative)
                implementation(Dependencies.Test.okio)
                implementation(Dependencies.MultiplatformSettings.test)
            }
        }
        val jvmMain by getting {}
        val jvmTest by getting
        val androidMain by getting {
            dependencies {
                implementation(Dependencies.Android.work)
            }
        }
        val androidTest by getting {
            dependencies {
                implementation(Dependencies.AndroidInstruments.androidTestRunner)
                implementation(Dependencies.AndroidInstruments.androidTestRules)
            }
        }
    }
}

dependencies {
    ksp(Dependencies.Test.mockativeProcessor)
}
