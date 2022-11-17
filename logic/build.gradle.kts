@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    id(libs.plugins.android.library.get().pluginId)
    id(libs.plugins.kotlin.multiplatform.get().pluginId)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
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
                implementation(libs.coroutines.core.map {
                    project.dependencies.create(it, closureOf<ExternalModuleDependency> {
                        version { strictly(libs.versions.coroutines.get()) }
                    })
                })
                implementation(libs.ktxSerialization)
                implementation(libs.ktxDateTime)
                implementation(libs.benAsherUUID)
                // the Dependency is duplicated between here and persistence build.gradle.kts
                implementation(libs.settings.kmp)

                // Okio
                implementation(libs.okio.core)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(project(":persistence-test"))
                // coroutines
                implementation(libs.coroutines.test)
                implementation(libs.turbine)

                // mocking
                implementation(libs.mockative.runtime)
                implementation(libs.okio.test)
                implementation(libs.settings.kmpTest)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.jna)
            }
        }
        val jvmTest by getting
        val androidMain by getting {
            dependencies {
                implementation(libs.paging3)
                implementation(libs.work)
            }
        }
        val androidAndroidTest by getting {
            dependencies {
                implementation(libs.androidtest.runner)
                implementation(libs.androidtest.rules)
                implementation(libs.androidtest.orchestratorRunner)
            }
        }
    }
}

dependencies {
    configurations
        .filter { it.name.startsWith("ksp") && it.name.contains("Test") }
        .forEach {
            add(it.name, libs.mockative.processor)
        }
    androidTestUtil(libs.androidtest.orchestratorUtil)
}

ksp {
    arg("mockative.stubsUnitByDefault", "true")
}
