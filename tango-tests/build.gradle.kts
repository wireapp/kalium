import com.wire.kalium.plugins.commonDokkaConfig

/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import com.wire.kalium.plugins.commonDokkaConfig

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    id(libs.plugins.android.library.get().pluginId)
    id(libs.plugins.kotlin.multiplatform.get().pluginId)
    id(libs.plugins.kalium.library.get().pluginId)
}

kaliumLibrary {
    multiplatform {
        // enableJs.set(false)
        enableIntegrationTests.set(true)
        // enableIntegrationTests.set(true)
    }
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                // implementation(project(":persistence"))
                // implementation(kotlin("integrationTest"))
                // coroutines
                implementation(libs.coroutines.core)
                implementation(libs.coroutines.test)
                implementation(libs.settings.kmp)
                implementation(libs.settings.kmpTest)
            }
        }
//         val androidMain by getting {
//             dependencies {
//                 implementation(libs.androidtest.runner)
//                 implementation(libs.androidtest.rules)
//                 implementation(libs.androidtest.core)
//             }
//         }

        val jvmTest by getting
        val jvmIntegrationTest by getting // {
//             dependencies {
//                 // implementation(kotlin("integrationTest"))
//
//                 // coroutines
//                 implementation(libs.coroutines.test)
//                 implementation(libs.turbine)
//
//                 // mocking
//                 implementation(libs.mockative.runtime)
//                 implementation(libs.okio.test)
//                 implementation(libs.settings.kmpTest)
//                 implementation(libs.mockk)
//             }
        // }

        tasks.withType<JavaExec> {
            jvmArgs = listOf("-Djava.library.path=/usr/local/lib/:./native/libs")
        }
    }
}

commonDokkaConfig()

tasks.withType<Wrapper> {
    gradleVersion = "7.3.1"
    distributionType = Wrapper.DistributionType.BIN
}
