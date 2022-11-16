import com.google.protobuf.gradle.GenerateProtoTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile

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

val codegenProject = project(":protobuf-codegen")
val generatedFilesBaseDir = file("generated")
generatedFilesBaseDir.mkdirs()

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
            kotlin.srcDir(generatedFilesBaseDir)
            dependencies {
                api(libs.pbandkRuntime)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val androidTest by getting {
            dependencies {
                implementation(libs.androidtest.runner)
                implementation(libs.androidtest.rules)
            }
        }
    }
}

val compileTasks = tasks.matching { it is KotlinCompile || it is KotlinNativeCompile }

codegenProject.tasks
    .matching { it.name == "generateProto" }
    .all {
        this as GenerateProtoTask
        compileTasks.forEach { compileTask ->
            compileTask.dependsOn(this)
        }
        // Always generate protobuf files. So we make sure they exist.
        outputs.upToDateWhen {
            false
        }
        doLast {
            outputSourceDirectorySet.srcDirs.forEach { generatedDirectory ->
                generatedFilesBaseDir.mkdirs()
                val targetDirectory = File(generatedFilesBaseDir, generatedDirectory.name)
                // Delete already existing files
                targetDirectory.deleteRecursively()

                // Move generated files to target directory
                val movingSucceeded = generatedDirectory.renameTo(targetDirectory)

                require(movingSucceeded) {
                    "Failed to move Generated protobuf files from '${generatedDirectory.absolutePath}' " +
                            "to destination directory '${targetDirectory.absolutePath}'"
                }
            }
        }
    }
