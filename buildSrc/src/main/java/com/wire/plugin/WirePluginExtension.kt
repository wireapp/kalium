package com.wire.plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import java.io.File
import javax.inject.Inject

@Suppress("UnnecessaryAbstractClass")
abstract class WirePluginExtension @Inject constructor(project: Project) {

    private val objects = project.objects

    var carthageParameters: Property<CarthageParameters> = objects.property(CarthageParameters::class.java)

    var defGeneratorParameters: Property<DefGeneratorParameters> = objects.property(DefGeneratorParameters::class.java)

    val tag: Property<String> = objects.property(String::class.java)

    fun defFileForArtifact(artifactDefinition: DefGeneratorArtifactDefinition): File  {
        val outputDir = defGeneratorParameters.get().defOutputDir
        return File("${outputDir}/${artifactDefinition.artifactName}/${artifactDefinition.artifactTarget}/${artifactDefinition.artifactName}.def")
    }

    fun headersDirForArtifact(artifactDefinition: DefGeneratorArtifactDefinition): File  {
        val headersDir = if (artifactDefinition.isXCFramework) {
            "${artifactDefinition.artifactName}.xcframework/${artifactDefinition.artifactTarget}/${artifactDefinition.artifactName}.framework/Headers"
        } else {
            "${artifactDefinition.artifactName}.framework/Headers"
        }
        return File(headersDir)
    }
}

data class CarthageParameters(
    val command: CarthageCommand,
    val platforms: List<CarthagePlatform>,
    val useXCFrameworks: Boolean,
)

enum class CarthageCommand(val commandString: String) {
    BOOTSTRAP("bootstrap"),
    UPDATE("update")
}

enum class CarthagePlatform(val platformString: String) {
    IOS(platformString = "iOS"),
    MACOS(platformString = "macos")
}

data class DefGeneratorParameters(
    var defOutputDir: File,
    var artifactDefinitions: List<DefGeneratorArtifactDefinition>
)

data class DefGeneratorArtifactDefinition(
    val artifactName: String,
    val artifactTarget: String,
    val isXCFramework: Boolean
)
