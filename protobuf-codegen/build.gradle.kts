import com.google.protobuf.gradle.builtins
import com.google.protobuf.gradle.generateProtoTasks
import com.google.protobuf.gradle.id
import com.google.protobuf.gradle.ofSourceSet
import com.google.protobuf.gradle.plugins
import com.google.protobuf.gradle.protobuf
import com.google.protobuf.gradle.protoc
import com.google.protobuf.gradle.remove

plugins {
    Plugins.jvm(this)
    Plugins.protobuf(this)
}

group = "com.wire.kalium"
version = "0.0.1-SNAPSHOT"

protobuf {
    generatedFilesBaseDir = "$projectDir/generated"
    protoc {
        artifact = "com.google.protobuf:protoc:3.19.4"
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
                id("pbandk")
            }
        }
    }
}

// Workaround to avoid compiling kotlin and java, since we are only using the generated code output
// https://github.com/streem/pbandk/blob/master/examples/gradle-and-jvm/build.gradle.kts
tasks {
    compileJava {
        enabled = false
    }
    compileKotlin {
        enabled = false
    }
}
