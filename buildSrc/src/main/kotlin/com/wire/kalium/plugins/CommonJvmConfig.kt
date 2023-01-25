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

package com.wire.kalium.plugins

import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

fun KotlinJvmTarget.commonJvmConfig(includeNativeInterop: Boolean) {
    compilations.all {
        kotlinOptions.jvmTarget = "1.8"
        kotlinOptions.freeCompilerArgs += "-opt-in=kotlin.RequiresOptIn"
    }
    testRuns.getByName("test").executionTask.configure {
        useJUnit()
        if (includeNativeInterop) {
            val runArgs = project.gradle.startParameter.systemPropertiesArgs.entries.map { "-D${it.key}=${it.value}" }
            jvmArgs(runArgs)
            if (System.getProperty("os.name").contains("Mac", true)) {
                jvmArgs("-Djava.library.path=/usr/local/lib/:../native/libs")
            }
        }
    }
}
