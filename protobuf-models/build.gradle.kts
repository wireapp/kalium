import com.google.protobuf.gradle.builtins
import com.google.protobuf.gradle.generateProtoTasks
import com.google.protobuf.gradle.id
import com.google.protobuf.gradle.ofSourceSet
import com.google.protobuf.gradle.plugins
import com.google.protobuf.gradle.remove
import com.google.protobuf.gradle.protobuf
import com.google.protobuf.gradle.protoc

plugins {
    Plugins.jvm(this)
//    Plugins.multiplatform(this)
    Plugins.protobuf(this)
}

group = "com.wire.kalium"
version = "0.0.1-SNAPSHOT"

//sourceSets{
//    create("protobuf"){
//        proto{
//            srcDir("src/protobuf/proto")
//        }
//    }
//}

//kotlin {
//    jvm {
//        compilations.all {
//            kotlinOptions.jvmTarget = "1.8"
//            kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
//        }
//        testRuns["test"].executionTask.configure {
//            useJUnit()
//        }
//    }
//    android()
//    iosX64()
//    js(IR) {
//        browser {
//            commonWebpackConfig {
//                cssSupport.enabled = true
//            }
//        }
//    }
//
//    sourceSets {
//        val commonMain by getting {
//            dependencies {
//                implementation("pro.streem.pbandk:pbandk-runtime:${Versions.pbandk}")
//            }
//        }
//    }
//}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.0.0-rc-2"
    }
    plugins {
        id("pbandk") {
            artifact = "pro.streem.pbandk:protoc-gen-pbandk-jvm:${Versions.pbandk}:jvm8@jar"
        }
    }
    generateProtoTasks {
        ofSourceSet("main").forEach { task ->
            task.builtins {
                remove("java")
            }
            task.plugins {
                id("pbandk") {
                    option("kotlin_package=com.wire.kalium.protobuf")
                }
            }
        }
    }
}
