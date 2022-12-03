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
            val enableiOS: Property<Boolean>
            val enableJs: Property<Boolean>
            val enableJsTests: Property<Boolean>
            val includeNativeInterop: Property<Boolean>
        }

        @get:Nested
        abstract val multiplatformConfiguration: MultiplatformConfiguration

        private val defaultConfiguration = object: Action<MultiplatformConfiguration> {
            override fun execute(t: MultiplatformConfiguration) {}
        }

        fun multiplatform(action: Action<MultiplatformConfiguration> = defaultConfiguration) {
            action.execute(multiplatformConfiguration)
            project.configureDefaultMultiplatform(
                enableiOS = multiplatformConfiguration.enableiOS.getOrElse(true),
                enableJs = multiplatformConfiguration.enableJs.getOrElse(true),
                enableJsTests = multiplatformConfiguration.enableJsTests.getOrElse(true),
                includeNativeInterop = multiplatformConfiguration.includeNativeInterop.getOrElse(false)
            )
        }
    }
}
