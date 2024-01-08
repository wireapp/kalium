/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

import com.google.protobuf.gradle.id
import com.google.protobuf.gradle.remove

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    kotlin("jvm")
    id(libs.plugins.protobuf.get().pluginId)
}

group = "com.wire.kalium"
version = "0.0.1-SNAPSHOT"

protobuf {
    generatedFilesBaseDir = "$projectDir/generated"
    protoc {
        artifact = "com.google.protobuf:protoc:3.24.0"
    }
    plugins {
        id("pbandk") {
            artifact = "pro.streem.pbandk:protoc-gen-pbandk-jvm:${libs.versions.pbandk.get()}:jvm8@jar"
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
