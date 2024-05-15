import com.wire.kalium.plugins.commonDokkaConfig
import com.wire.kalium.plugins.commonJvmConfig
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmCompilation
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

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

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    kotlin("multiplatform")
    alias(libs.plugins.kotlin.serialization)
    id(libs.plugins.sqldelight.get().pluginId)
}

sqldelight {
    databases {
        create("UserDatabase") {
            dialect(libs.sqldelight.dialect.get().toString())
            packageName.set("com.wire.kalium.persistence")
            val sourceFolderName = "db_user"
            srcDirs.setFrom(listOf("src/jvmMain/$sourceFolderName"))
            schemaOutputDirectory.set(file("src/jvmMain/$sourceFolderName/schemas"))
        }

        create("GlobalDatabase") {
            dialect(libs.sqldelight.dialect.get().toString())
            packageName.set("com.wire.kalium.persistence")
            val sourceFolderName = "db_global"
            srcDirs.setFrom(listOf("src/jvmMain/$sourceFolderName"))
            schemaOutputDirectory.set(file("src/jvmMain/$sourceFolderName/schemas"))
        }
    }
}

kotlin {
    applyDefaultHierarchyTemplate()
    val jvmTarget = jvm {
        commonJvmConfig(includeNativeInterop = false)
    }

    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(project(":persistence"))
                implementation(project(":util"))
                implementation(libs.sqldelight.jvmDriver)
                implementation(libs.sqlite.xerialDriver)
            }
        }
        val jvmTest by getting
    }
}

commonDokkaConfig()
