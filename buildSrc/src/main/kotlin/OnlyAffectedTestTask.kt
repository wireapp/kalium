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

    @Input
    var ignoredModules: List<String> = emptyList()

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

        if (!hasToRunAllTests() && (affectedModules.isEmpty() || affectedModules.first().isEmpty())) {
            println("\uD83E\uDD8B It is not necessary to run any test, ending here to free up some resources.")
            return
        }

        executeTask(affectedModules)
    }

    private fun executeTask(affectedModules: Set<String>) {
        val tasksName = mutableListOf<String>()
        val hasToRunAllTests = hasToRunAllTests()
        project.childProjects.values
            .filter { computeModulesPredicate(hasToRunAllTests, affectedModules.contains(it.name) && !ignoredModules.contains(it.name)) }
            .forEach { childProject ->
                tasksName.addAll(childProject.tasks
                    .filter { it.name.equals(targetTestTask, true) }
                    .map { task -> "${childProject.name}:${task.name}" }.toList()
                )
            }

        tasksName.forEach(::runTargetTask)
    }

    private fun runTargetTask(targetTask: String) {
        println("\uD83D\uDD27 Running tests on '$targetTask'.")
        project.exec {
            args(targetTask)
            executable("./gradlew")
        }
    }

    /**
     * Get the predicate to compute if the module should be included or not in the test
     */
    private fun computeModulesPredicate(allTests: Boolean, modulesPredicate: Boolean) = when {
        allTests == true -> true
        else -> modulesPredicate
    }

    /**
     * Check if we have to run all tests, by looking at changes on libs versions
     */
    private fun hasToRunAllTests(): Boolean {
        val isVersionsFileChanged =
            "bash -c 'git diff --quiet origin/develop -- ${project.rootDir}/gradle/libs.versions.toml ; echo $?'".runCommandWithExitCode()
        val isRootBuildFileChanged =
            "bash -c 'git diff --quiet origin/develop -- ${project.rootDir}/build.gradle.kts ; echo $?'".runCommandWithExitCode()

        return (isVersionsFileChanged != 0 || isRootBuildFileChanged != 0).also {
            if (it) {
                println("\uD83D\uDD27 Running all tests because there are changes at the root level")
            }
        }
    }

    /**
     * Helper enum, that allow to define configurations, that will be automatically added (if you want)
     *
     * @param taskName how the task will be named when registered
     * @param testTarget the target test task that would be wrapped in this "smart" execution
     */
    enum class TestTaskConfiguration(val taskName: String, val testTarget: String) {
        ANDROID_TEST_TASK("connectedAndroidOnlyAffectedTest", "connectedAndroidTest"),
        IOS_TEST_TASK("iOSOnlyAffectedTest", "iosX64Test")
    }

}
