import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import org.ajoberstar.grgit.Grgit
import java.io.File
import org.ajoberstar.grgit.Credentials


object Customization {
    val jsonReader = JsonSlurper()
    val properties = java.util.Properties()


    fun defaultBuildtimeConfiguration(rootDir: File): BuildTimeConfiguration? {
        val customCheckoutDir = "$rootDir/custom"

        val localProperties = File("local.properties")
        if (localProperties.exists()) {
            properties.load(localProperties.inputStream())
        }

        val wireConfigFile = File("$rootDir/default.json")
        val defaultConfig = jsonReader.parseText(wireConfigFile.readText()) as MutableMap<String, Any>


        val customRepository = System.getenv("CUSTOM_REPOSITORY") ?: properties.getProperty("CUSTOM_REPOSITORY")

        if (customRepository.isNullOrEmpty()) {
            return BuildTimeConfiguration(defaultConfig, null)
        } else {
            return prepareCustomizationEnvironment(defaultConfig, customCheckoutDir)
        }
    }


    //     Will check out custom repo, if any, and load its configuration, merging it on top of the default configuration
    fun prepareCustomizationEnvironment(
        defaultConfig: MutableMap<String, Any>,
        customCheckoutDir: String
    ): BuildTimeConfiguration {

        val customRepository = System.getenv("CUSTOM_REPOSITORY") ?: properties.getProperty("CUSTOM_REPOSITORY")
        if (customRepository.isEmpty()) {
            throw  GradleException("No custom repo")
        }

        val customFolder = System.getenv("CUSTOM_FOLDER") ?: properties.getProperty("CUSTOM_FOLDER")
        if (customFolder.isEmpty()) {
            throw  GradleException("Custom repo specified, but not custom folder")
        }

        val clientFolder = System.getenv("CLIENT_FOLDER") ?: properties.getProperty("CLIENT_FOLDER")
        if (clientFolder.isEmpty()) {
            throw  GradleException("Custom repo specified, but not client folder")
        }

        val grGitUser = System.getenv("GRGIT_USER") ?: properties.getProperty("GRGIT_USER")
        if (grGitUser.isEmpty()) {
            throw  GradleException("Custom repo specified, but no grgit user provided")
        }
        val grGitPassword = System.getenv("GRGIT_PASSWORD") ?: properties.getProperty("GRGIT_PASSWORD")

        val customDirPath = "$customCheckoutDir/$customFolder/$clientFolder"
        val customConfigFile = File("$customDirPath/custom.json")

        // clean up
        try {
            val file = File(customCheckoutDir)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {

        }


        val credentials = Credentials(grGitUser, grGitPassword)
        Grgit.clone(mapOf("dir" to customCheckoutDir, "uri" to customRepository, "credentials" to credentials))

        val customConfig = jsonReader.parseText(customConfigFile.readText()) as MutableMap<String, Any>
        for (item in customConfig) {
            defaultConfig.put(item.key, item.value)
        }


        val buildtimeConfiguration = BuildTimeConfiguration(defaultConfig, customDirPath)


        return buildtimeConfiguration
    }

    class BuildTimeConfiguration(val configuration: Map<String, Any>, val customResourcesPath: String?) {
        fun isCustomBuild(): Boolean {
            return customResourcesPath != null
        }
    }


}
