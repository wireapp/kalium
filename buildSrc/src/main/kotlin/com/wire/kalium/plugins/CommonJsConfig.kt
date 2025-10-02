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

package com.wire.kalium.plugins

import org.gradle.api.Project
import org.gradle.kotlin.dsl.invoke
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

fun KotlinMultiplatformExtension.commonJsConfig(project: Project, jsModuleNameOverride: String?, enableJsTests: Boolean) {
    js {
        if (jsModuleNameOverride != null) {
            moduleName = jsModuleNameOverride
        }
        browser {
//                     // Not needed for now, but if we include UI with CSS in the future, we can enable it
//                     commonWebpackConfig {
//                         cssSupport.enabled = true
//                     }
            testTask {
                enabled = enableJsTests
                useMocha {
                    timeout = "5s"
                }
            }
        }
    }
    sourceSets {
        getByName("jsMain") {
            dependencies {
                implementation(project.library("ktx-atomicfu-runtime"))
            }
        }
    }
}
