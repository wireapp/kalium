/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

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
    lateinit var configuration: TestTaskConfiguration

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
                    .filter { it.name.equals(configuration.testTarget, true) }
                    .map { task ->
                        println("Adding task: ${childProject.name}:${task.name}")
                        "${childProject.name}:${task.name}"
                    }.toList()
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
     * Check if we have to run all tests, by looking at untracked by dag-command files [globalBuildSettingsFiles].
     */
    private fun hasToRunAllTests(): Boolean {
        val globalBuildSettingsFiles = listOf(
            "gradle/libs.versions.toml",
            "build.gradle.kts",
            "gradle.properties",
            "settings.gradle.kts",
        )

        val anySettingsFileChanged = globalBuildSettingsFiles.any { relativePath ->
            val exitCode = "git diff --quiet origin/develop -- ${project.rootDir}/$relativePath".execute().exitValue()
            exitCode != 0
        }
        if (anySettingsFileChanged) {
            println("\uD83D\uDD27 Running all tests because there are changes at the root level")
        }
        return anySettingsFileChanged
    }

    /**
     * Helper enum, that allow to define configurations, that will be automatically added (if you want)
     *
     * @param taskName how the task will be named when registered
     * @param testTarget the target test task that would be wrapped in this "smart" execution
     */
    enum class TestTaskConfiguration(val taskName: String, val testTarget: String) {
        ANDROID_INSTRUMENTED_TEST_TASK("connectedAndroidOnlyAffectedTest", "connectedAndroidTest"),
        ANDROID_UNIT_TEST_TASK("androidUnitOnlyAffectedTest", "testDebugUnitTest"),
        IOS_TEST_TASK("iOSOnlyAffectedTest", "iosX64Test")
    }

}
