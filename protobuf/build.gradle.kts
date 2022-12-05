import com.google.protobuf.gradle.GenerateProtoTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    id(libs.plugins.android.library.get().pluginId)
    id(libs.plugins.kotlin.multiplatform.get().pluginId)
    id(libs.plugins.kalium.library.get().pluginId)
}

kaliumLibrary {
    multiplatform()
}

val codegenProject = project(":protobuf-codegen")
val generatedFilesBaseDir = file("generated")
generatedFilesBaseDir.mkdirs()

kotlin {
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
