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

import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsTargetDsl

fun KotlinJsTargetDsl.commonJsConfig(
    jsModuleNameOverride: String?,
    enableJsTests: Boolean
) {
    jsModuleNameOverride?.let { outputModuleName.set(it) }

    compilerOptions {
        freeCompilerArgs.add("-Xes-long-as-bigint")
    }

    browser {
        testTask {
            enabled = enableJsTests
            useMocha { timeout = "5s" }
        }
        // if later we need to enable CSS
        // commonWebpackConfig { cssSupport { enabled.set(true) } }
    }
}
