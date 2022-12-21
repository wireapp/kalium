import org.gradle.api.Project
import java.io.File
import java.util.Properties
import java.util.concurrent.TimeUnit

/**
 * Convenience method to obtain a property from `$projectRoot/local.properties` file
 * without passing the project param
 */
fun <T> Project.getLocalProperty(propertyName: String, defaultValue: T): T {
    return getLocalProperty(propertyName, defaultValue, this)
}

/**
 * Util to obtain property declared on `$projectRoot/local.properties` file or default
 */
@Suppress("UNCHECKED_CAST")
internal fun <T> getLocalProperty(propertyName: String, defaultValue: T, project: Project): T {
    val localProperties = Properties().apply {
        val localPropertiesFile = project.rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            load(localPropertiesFile.inputStream())
        }
    }

    val localValue = localProperties.getOrDefault(propertyName, defaultValue) as? T ?: defaultValue
    println("> Reading local prop '$propertyName' with value: $localValue")
    return localValue
}

/**
 * Run command and return the output
 */
fun String.runCommand(workingDir: File = File("./")): String? = try {
    val proc = processCommand(workingDir)
    proc!!.inputStream.bufferedReader().readText().trim()
} catch (e: Exception) {
    println("Error running command: $this")
    e.printStackTrace()
    null
}

/**
 * Run command and return the exit code
 */
fun String.runCommandWithExitCode(workingDir: File = File("./")): Int = try {
    val proc = processCommand(workingDir)
    proc!!.exitValue()
} catch (e: Exception) {
    println("Error running command: $this")
    e.printStackTrace()
    999
}

private fun String.processCommand(workingDir: File): Process? {
    val parts = this.split("\\s".toRegex())
    val proc = ProcessBuilder(*parts.toTypedArray())
        .directory(workingDir)
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .redirectError(ProcessBuilder.Redirect.PIPE)
        .start()

    proc.waitFor(1, TimeUnit.MINUTES)
    proc.inputStream.bufferedReader().readText().trim()
    return proc
}
