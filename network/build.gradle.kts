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
    // Remove instrumented tests as Network tests can run as Unit tests for Android
    sourceSets.remove(sourceSets["androidTest"])
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
    iosX64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":protobuf"))
                api(project(":logger"))

                // coroutines
                implementation(libs.coroutinesCore.map {
                    project.dependencies.create(it, closureOf<ExternalModuleDependency> {
                        version { strictly(libs.versions.coroutines.get()) }
                    })
                })

                // ktor
                api(libs.ktorCore)
                implementation(libs.ktorUtils)
                implementation(libs.ktorJson)
                implementation(libs.ktorSerialization)
                implementation(libs.ktorLogging)
                implementation(libs.authClient)
                implementation(libs.webSocket)
                implementation(libs.contentNegotiation)
                implementation(libs.encoding)

                // Okio
                implementation(libs.okioCore)
                implementation(libs.okioTest)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                // coroutines
                implementation(libs.coroutinesCore)
                // ktor test
                implementation(libs.ktorMock)
            }
        }

        fun org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet.addCommonKotlinJvmSourceDir() {
            kotlin.srcDir("src/commonJvmAndroid/kotlin")
        }

        val jvmMain by getting {
            addCommonKotlinJvmSourceDir()
            dependencies {
                implementation(libs.okHttp)
            }
        }
        val jvmTest by getting
        val androidMain by getting {
            addCommonKotlinJvmSourceDir()
            dependencies {
                implementation(libs.okHttp)
            }
        }
        val androidTest by getting
        val iosX64Main by getting {
            dependencies {
                implementation(libs.iosHttp)
            }
        }
    }
}
