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

import org.gradle.api.Plugin
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Nested

class LibraryPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        group = KaliumBuild.GROUP
        version = KaliumBuild.VERSION

        extensions.create("kaliumLibrary", Extension::class.java)
    }

    abstract class Extension(private val project: Project) {
        interface MultiplatformConfiguration {
            val enableApple: Property<Boolean>
            val enableJs: Property<Boolean>
            val jsModuleName: Property<String?>
            val enableJsTests: Property<Boolean>
            val includeNativeInterop: Property<Boolean>
            val enableIntegrationTests: Property<Boolean>
        }

        @get:Nested
        abstract val multiplatformConfiguration: MultiplatformConfiguration

        private val defaultConfiguration = object : Action<MultiplatformConfiguration> {
            override fun execute(t: MultiplatformConfiguration) = Unit
        }

        fun multiplatform(action: Action<MultiplatformConfiguration> = defaultConfiguration) {
            action.execute(multiplatformConfiguration)
            project.configureDefaultMultiplatform(
                enableApple = multiplatformConfiguration.enableApple.getOrElse(true),
                enableJs = multiplatformConfiguration.enableJs.getOrElse(true),
                jsModuleNameOverride = multiplatformConfiguration.jsModuleName.orNull,
                enableJsTests = multiplatformConfiguration.enableJsTests.getOrElse(true),
                includeNativeInterop = multiplatformConfiguration.includeNativeInterop.getOrElse(false),
                enableIntegrationTests = multiplatformConfiguration.enableIntegrationTests.getOrElse(false)
            )
        }
    }
}
