import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

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
                api(project(":logger"))

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
        fun KotlinSourceSet.addCommonKotlinJvmSourceDir() {
            kotlin.srcDir("src/commonJvmAndroid/kotlin")
        }
        val jvmMain by getting {
            addCommonKotlinJvmSourceDir()
            dependencies {
                implementation(Dependencies.Protobuf.wireJvmMessageProto)
            }
        }
        val jvmTest by getting
        val androidMain by getting {
            addCommonKotlinJvmSourceDir()
            dependencies {
                implementation(Dependencies.Android.work)
                implementation(Dependencies.Protobuf.wireJvmMessageProto) {
                    // Don't use the runtime Protobuf included in wire. We can use Protobuf Lite instead
                    exclude(module = "protobuf-java")
                }
                implementation(Dependencies.Protobuf.protobufLite)
            }
        }
        val androidTest by getting
    }
}

dependencies {
    ksp(Dependencies.Test.mockativeProcessor)
}
