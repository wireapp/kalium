import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * This task will run only the [connectedAndroidTest] for affected modules and dependants.
 *
 * This task dependsOn: [https://github.com/leandroBorgesFerreira/dag-command]
 * That will generate for us a list of affected modules
 *
 */
open class OnlyAffectedConnectedTestTask : DefaultTask() {

    init {
        group = "verification"
        description = "Installs and runs the tests for debug on connected devices (Only for affected modules)."

        setDependsOn(mutableListOf("dag-command"))
    }

    @TaskAction
    fun runOnlyAffectedConnectedTest() {
        var affectedModules: Set<String> = mutableSetOf()
        File("${project.buildDir}/dag-command/affected-modules.json").useLines {
            affectedModules = it.joinToString()
                .removeSurrounding("[", "]")
                .replace("\"", "")
                .split(",")
                .toSet()
        }

        if (affectedModules.isEmpty() || affectedModules.first().isEmpty()) {
            println("\uD83E\uDD8B It is not necessary to run any test, ending here to free up some resources.")
            return
        }

        affectedModules.filter { targetModules.contains(it) }.forEach { module ->
            val testTask = "$module:$testTaskSuffix"
            println("\uD83D\uDD27 Running tests on '$testTask'")
            project.exec {
                args(testTask)
                executable("./gradlew")
            }
        }
    }

    private companion object {
        private val targetModules = listOf("cryptography", "persistence", "logic")
        private const val testTaskSuffix = "connectedAndroidTest"
    }

}
