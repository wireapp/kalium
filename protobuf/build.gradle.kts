import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

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
        consumerProguardFiles("consumer-proguard-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

val codegenProject = project(":protobuf-codegen")

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
    js(IR) {
        browser {
            commonWebpackConfig {
                cssSupport.enabled = true
            }
            testTask {
                useMocha {
                    timeout = "5s"
                }
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir(codegenProject.file("build/generated/source/proto/main/pbandk"))
            dependencies {
                api("pro.streem.pbandk:pbandk-runtime:${Versions.pbandk}")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

val compileTasks = tasks.matching { it is KotlinCompile }

codegenProject.tasks
    .matching { it.name == "generateProto" }
    .all {
        compileTasks.forEach { compileTask ->
            compileTask.dependsOn(this)
        }
    }
