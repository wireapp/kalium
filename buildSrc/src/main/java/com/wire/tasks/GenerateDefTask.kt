package com.wire.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import com.wire.plugin.DefGeneratorParameters


abstract class GenerateDefTask : DefaultTask() {

    init {
        description = "Generate .def files for cinterop"
        group = "wire"
    }

    @get:Input
    @get:Option(option = "defGeneratorParameters", description = "The set of parameters to run use when generating .def files")
    abstract val parameters: Property<DefGeneratorParameters>

    @get:Input
    @get:Option(option = "tag", description = "A Tag to be used for debug")
    @get:Optional
    abstract val tag: Property<String>

    @TaskAction
    fun generateDef() {
        val prettyTag = tag.orNull?.let { "[$it]" } ?: ""
        val artifactDefinitions = parameters.get().artifactDefinitions

        var defOutputDir = parameters.get().defOutputDir
        val carthageBuildDir = project.projectDir.resolve("Carthage/Build/")

        logger.lifecycle("$prettyTag ${carthageBuildDir.absolutePath}")

        artifactDefinitions.forEach { artifactDefinition ->

            logger.lifecycle("$prettyTag generating .def for ${artifactDefinition.artifactName} for target triplet ${artifactDefinition.artifactTarget}")

            if (artifactDefinition.isXCFramework) {
                val xcFrameworkDirs = carthageBuildDir.walkTopDown().filter { it.name == "${artifactDefinition.artifactName}.xcframework" }
                logger.lifecycle("$prettyTag found dir ${xcFrameworkDirs.first()}")
                val headers = xcFrameworkDirs
                    .first()
                    .resolve("${artifactDefinition.artifactTarget}/${artifactDefinition.artifactName}.framework/Headers")
                    .walkTopDown()
                    .filter { it.extension == "h" && it.name != "${artifactDefinition.artifactName}.h" }

                headers.forEach { logger.lifecycle("$prettyTag header at: ${it.absolutePath}") }

                if (!defOutputDir.exists()) {
                    defOutputDir.mkdirs()
                }
                val targetDefDir = defOutputDir.resolve("${artifactDefinition.artifactName}/${artifactDefinition.artifactTarget}")
                if (!targetDefDir.exists()) {
                    targetDefDir.mkdirs()
                }
                var defFile = targetDefDir.resolve("${artifactDefinition.artifactName}.def")
                defFile.createNewFile()
                defFile.writeText(
                    """
                    language = Objective-C
                    headers = ${headers.map{ it.name }.joinToString(" ")}
                    excludeDependentModules = true
                    compilerOpts = -framework ${artifactDefinition.artifactName}
                    linkerOpts = -framework ${artifactDefinition.artifactName}
                    """.trimIndent()
                )
            }
            else {
                TODO("handle regular frameworks as well")
            }
        }
    }
}
