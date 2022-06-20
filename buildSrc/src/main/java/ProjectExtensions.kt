import org.gradle.api.Project
import java.util.Properties

/**
 * Util to obtain property declared on `$projectRoot/local.properties` file.
 *
 * @param propertyName the name of declared property
 * @param defaultValue fallback in case the property doesn't exist
 * @param project the project reference
 *
 * @return the value of property name, otherwise a [defaultValue]
 */
internal fun getLocalProperty(propertyName: String, defaultValue: Any, project: Project): Any {
    val localProperties = Properties().apply {
        val localPropertiesFile = project.rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            load(localPropertiesFile.inputStream())
        }
    }

    return localProperties.getOrDefault(propertyName, defaultValue)
}

/**
 * Convenience method to obtain a property from `$projectRoot/local.properties` file
 * without passing the project param, this can be used in build.gradle.kts files
 */
fun Project.getLocalPropertyOrDefault(propertyName: String, defaultValue: Any): Any {
    return getLocalProperty(propertyName, defaultValue, this)
}
