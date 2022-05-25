import groovy.json.JsonSlurper


object Customization {
    private val rootDir = System.getProperty("user.dir")


    fun defaultBuildtimeConfiguration(): BuildTimeConfiguration? {
        val properties = java.util.Properties()
        val localProperties = java.io.File("local.properties")
        if (localProperties.exists()) {
            properties.load(localProperties.inputStream())
        }


        val jsonReader = JsonSlurper()
        val wireConfigFile = java.io.File("$rootDir/default.json")
        val defaultConfig = jsonReader.parseText(wireConfigFile.readText()) as Map<String, Any>


        val customRepository = System.getenv("CUSTOM_REPOSITORY") ?: properties.getProperty("CUSTOM_REPOSITORY")

        if (customRepository.isNullOrEmpty()) {
            return BuildTimeConfiguration(defaultConfig, null)
        }

        return null
    }

    class BuildTimeConfiguration(val configuration: Map<String, Any>, val customResourcesPath: String?) {
        fun isCustomBuild(): Boolean {
            return customResourcesPath != null
        }
    }


    // configuration and external config
    val buildtimeConfiguration = defaultBuildtimeConfiguration()

    val customCheckoutDir = "$rootDir/custom"


}
