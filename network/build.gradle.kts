@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    id(libs.plugins.android.library.get().pluginId)
    id(libs.plugins.kotlin.multiplatform.get().pluginId)
    alias(libs.plugins.kotlin.serialization)
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
                implementation(libs.coroutines.core.map {
                    project.dependencies.create(it, closureOf<ExternalModuleDependency> {
                        version { strictly(libs.versions.coroutines.get()) }
                    })
                })

                // ktor
                api(libs.ktor.core)
                implementation(libs.ktor.utils)
                implementation(libs.ktor.json)
                implementation(libs.ktor.serialization)
                implementation(libs.ktor.logging)
                implementation(libs.ktor.authClient)
                implementation(libs.ktor.webSocket)
                implementation(libs.ktor.contentNegotiation)
                implementation(libs.ktor.encoding)

                // Okio
                implementation(libs.okio.core)
                implementation(libs.okio.test)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                // coroutines
                implementation(libs.coroutines.test)
                // ktor test
                implementation(libs.ktor.mock)
            }
        }

        fun org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet.addCommonKotlinJvmSourceDir() {
            kotlin.srcDir("src/commonJvmAndroid/kotlin")
        }

        val jvmMain by getting {
            addCommonKotlinJvmSourceDir()
            dependencies {
                implementation(libs.ktor.okHttp)
            }
        }
        val jvmTest by getting
        val androidMain by getting {
            addCommonKotlinJvmSourceDir()
            dependencies {
                implementation(libs.ktor.okHttp)
            }
        }
        val androidTest by getting
        val iosX64Main by getting {
            dependencies {
                implementation(libs.ktor.iosHttp)
            }
        }
    }
}
