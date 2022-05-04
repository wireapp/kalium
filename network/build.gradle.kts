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
configurations.all {
    resolutionStrategy {
        force(Dependencies.Coroutines.core)
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
    iosX64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":logger"))

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
        fun org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet.addCommonKotlinJvmSourceDir() {
            kotlin.srcDir("src/commonJvmAndroid/kotlin")
        }
        val jvmMain by getting {
            addCommonKotlinJvmSourceDir()
            dependencies {
                implementation(Dependencies.Ktor.okHttp)
                implementation(Dependencies.Protobuf.wireJvmMessageProto)
            }
        }
        val jvmTest by getting
        val androidMain by getting {
            addCommonKotlinJvmSourceDir()
            dependencies {
                implementation(Dependencies.Ktor.okHttp)
                implementation(Dependencies.Protobuf.wireJvmMessageProto) {
                    // Don't use the runtime Protobuf included in wire. We can use Protobuf Lite instead
                    exclude(module = "protobuf-java")
                }
                implementation(Dependencies.Protobuf.protobufLite)
            }
        }
        val androidTest by getting
        val iosX64Main by getting {
            dependencies {
                implementation(Dependencies.Ktor.iosHttp)
            }
        }
    }
}
