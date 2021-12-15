package com.wire.tasks
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import com.wire.plugin.CarthageParameters

abstract class CarthageTask : DefaultTask() {

    init {
        description = "Run Carthage"
        group = "wire"
    }

    @get:Input
    @get:Option(option = "carthageParameters", description = "The set of parameters to run Carthage with")
    abstract val parameters: Property<CarthageParameters>

    @get:Input
    @get:Option(option = "tag", description = "A Tag to be used for debug")
    @get:Optional
    abstract val tag: Property<String>

    @TaskAction
    fun carthageAction() {
        val prettyTag = tag.orNull?.let { "[$it]" } ?: ""

        val platforms = parameters.get().platforms.joinToString(" ")
        logger.lifecycle("$prettyTag carthage invocation: carthage ${parameters.get().command.commandString} --cache-builds --platform $platforms ${if(parameters.get().useXCFrameworks) "--use-xcframeworks" else ""}")

        project.exec {
            this.executable = "carthage"
            this.workingDir = project.projectDir

            this.args(mutableListOf<Any?>().apply {
                add("${parameters.get().command.commandString}")
                add("--cache-builds")
                add("--platform")
                parameters.get().platforms.forEach {
                    add(it.platformString)
                }
                if (parameters.get().useXCFrameworks) {
                    add("--use-xcframeworks")
                }
            })
        }
    }
}
