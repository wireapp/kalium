package com.wire.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import com.wire.tasks.*

const val EXTENSION_NAME = "wire"

abstract class WirePlugin : Plugin<Project> {
    override fun apply(project: Project) {

        val extension = project.extensions.create(EXTENSION_NAME, WirePluginExtension::class.java, project)

        project.tasks.register("carthage", CarthageTask::class.java) {
            this.tag.set(extension.tag)
            this.parameters.set(extension.carthageParameters)
        }

        project.tasks.register("defgen", GenerateDefTask::class.java) {
            this.tag.set(extension.tag)
            this.parameters.set(extension.defGeneratorParameters)
        }
    }
}
