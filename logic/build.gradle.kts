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
    testOptions {
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }
    // Run only Instrumented tests. No need to run Unit AND Instrumented
    // We have JVM tests if we want to run quickly on our machines
    sourceSets.remove(sourceSets["test"])
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
                implementation(project(":persistence-test"))
                // coroutines
                implementation(Dependencies.Coroutines.test)
                implementation(Dependencies.Test.turbine)

                // mocking
                implementation(Dependencies.Test.mockative)
                implementation(Dependencies.Test.okio)
                implementation(Dependencies.MultiplatformSettings.test)
            }
        }
        val jvmMain by getting {
            dependencies {
	        implementation(Dependencies.Calling.jna)
            }
        }
        val jvmTest by getting
        val androidMain by getting {
            dependencies {
                implementation(Dependencies.Android.paging3)
                implementation(Dependencies.Android.work)
            }
        }
        val androidAndroidTest by getting {
            dependencies {
                implementation(Dependencies.AndroidInstruments.androidTestRunner)
                implementation(Dependencies.AndroidInstruments.androidTestRules)
                implementation(Dependencies.AndroidInstruments.androidxOrchestratorRunner)
            }
        }
    }
}

dependencies {
    configurations
        .filter { it.name.startsWith("ksp") && it.name.contains("Test") }
        .forEach {
            add(it.name, Dependencies.Test.mockativeProcessor)
        }
    androidTestUtil(Dependencies.AndroidInstruments.androidxOrchestratorUtil)
}

ksp {
    arg("mockative.stubsUnitByDefault", "true")
}
