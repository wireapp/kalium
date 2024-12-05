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
    kotlin("jvm")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

kotlin {
    sourceSets {
        val test by getting {
            kotlin.srcDir("src/integrationTest/kotlin")
            dependencies {
                implementation(project(":network"))
                implementation(project(":logic"))
                implementation(project(":persistence"))
                implementation(project(":mocks"))
                implementation(project(":cryptography"))
                implementation(libs.kotlin.test)
                implementation(libs.settings.kmpTest)

                implementation(libs.ktor.utils)
                implementation(libs.coroutines.core)
                implementation(libs.ktxDateTime)
                // coroutines
                implementation(libs.coroutines.test)
                implementation(libs.turbine)
                implementation(libs.ktxSerialization)
                implementation(libs.ktor.serialization)
                implementation(libs.ktor.okHttp)
                implementation(libs.ktor.contentNegotiation)
                implementation(libs.ktor.json)
                implementation(libs.ktor.authClient)
                implementation(libs.okhttp.loggingInterceptor)

                implementation(libs.faker)

                // ktor test
                implementation(libs.ktor.mock)
            }
        }

        tasks.withType<JavaExec> {
            jvmArgs = listOf("-Djava.library.path=/usr/local/lib/:./native/libs")
        }
    }
}
