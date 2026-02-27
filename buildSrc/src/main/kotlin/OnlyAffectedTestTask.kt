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

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.support.get
import org.gradle.process.ExecOperations

/**
 * This task will run only the tests for affected modules and dependants when affected-module
 * information is available in [AFFECTED_MODULES_FILE].
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
        description = "Runs tests for affected modules when available, otherwise runs all tests."
        setDependsOn(mutableListOf("dag-command"))
    }

    @TaskAction
    fun runOnlyAffectedConnectedTest() {
        val affectedModules = readAffectedModules()
        val missingAffectedModulesData = affectedModules == null
        val runAllTests = hasToRunAllTests() || missingAffectedModulesData

        if (!runAllTests && affectedModules.orEmpty().isEmpty()) {
            println("\uD83E\uDD8B It is not necessary to run any test, ending here to free up some resources.")
            return
        }

        executeTask(
            affectedModules = affectedModules.orEmpty(),
            runAllTests = runAllTests,
            missingAffectedModulesData = missingAffectedModulesData
        )
    }

    private fun executeTask(affectedModules: Set<String>, runAllTests: Boolean, missingAffectedModulesData: Boolean) {
        val tasksName = mutableListOf<String>()
        project.subprojects
            .filter { (runAllTests || affectedModules.contains(it.path)) && !ignoredModules.contains(it.path) }
            .forEach { childProject ->
                val targetTaskName = childProject.tasks.names.firstOrNull { it.equals(configuration.testTarget, true) }
                targetTaskName?.let { taskName ->
                    println("Adding task: ${childProject.path}:$taskName")
                    tasksName.add("${childProject.path}:$taskName")
                }
            }

        if (missingAffectedModulesData) {
            println("\uD83D\uDD27 Running all tests because affected-modules data is unavailable.")
        }
        tasksName.forEach(::runTargetTask)
    }

    private fun readAffectedModules(): Set<String>? {
        val affectedModulesFile = project.layout.buildDirectory.file(AFFECTED_MODULES_FILE).get().asFile
        if (!affectedModulesFile.exists()) {
            println("\uD83D\uDD27 Missing '$AFFECTED_MODULES_FILE', falling back to all modules.")
            return null
        }

        return affectedModulesFile.readText()
            .trim()
            .removeSurrounding("[", "]")
            .split(",")
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotBlank() }
            .toSet()
    }

    private fun runTargetTask(targetTask: String) {
        println("\uD83D\uDD27 Running tests on '$targetTask'.")
        val execOperations = services.get<ExecOperations>()
        execOperations.exec {
            args(targetTask)
            executable(if (System.getProperty("os.name").lowercase().contains("windows")) "gradlew.bat" else "./gradlew")
        }
    }

    /**
     * Check if we have to run all tests by looking at root-level build files [globalBuildSettingsFiles].
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
    enum class TestTaskConfiguration(val taskName: String, val testTarget: String, val ignoredModules: List<String> = emptyList()) {
        ANDROID_INSTRUMENTED_TEST_TASK("connectedAndroidOnlyAffectedTest", "connectedAndroidTest", IGNORED_MODULES),
        ANDROID_UNIT_TEST_TASK("androidUnitOnlyAffectedTest", "testDebugUnitTest", IGNORED_MODULES),
        IOS_TEST_TASK("iOSOnlyAffectedTest", "iosSimulatorArm64Test", IGNORED_MODULES);
    }

    private companion object {
        val IGNORED_MODULES = listOf(":data:protobuf", ":tools:protobuf-codegen")
        const val AFFECTED_MODULES_FILE = "dag-command/affected-modules.json"
    }
}
