import OnlyAffectedTestTask.TestTaskConfiguration
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * This task will run only the tests for affected modules and dependants.
 *
 * This task dependsOn: [https://github.com/leandroBorgesFerreira/dag-command]
 * That will generate for us a list of affected modules
 *
 * You can define your own task by manually.
 * Or you can add it to the [TestTaskConfiguration] enum, and it will be added automatically
 *
 */
open class OnlyAffectedTestTask : DefaultTask() {

    @Input
    lateinit var targetTestTask: String

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

        executeTask(affectedModules)
    }

    private fun runTargetTask(targetTask: String) {
        println("\uD83D\uDD27 Running tests on '$targetTask'.")
        project.exec {
            args(targetTask)
            executable("./gradlew")
        }
    }

    private fun executeTask(affectedModules: Set<String>) {
        val tasksName = mutableListOf<String>()
        project.childProjects.values
            .filter { affectedModules.contains(it.name) }
            .forEach { childProject ->
                tasksName.addAll(childProject.tasks
                    .filter { it.name.equals(targetTestTask, true) }
                    .map { task -> "${childProject.name}:${task.name}" }.toList()
                )
            }

        tasksName.forEach { runTargetTask(it) }
    }

    enum class TestTaskConfiguration(val taskName: String, val testTarget: String) {
        ANDROID_TEST_TASK("connectedAndroidOnlyAffectedTest", "connectedAndroidTest"),
        IOS_TEST_TASK("connectedAIOSOnlyAffectedTest", "iosX64Test")
    }

}
