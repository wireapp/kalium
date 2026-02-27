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

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DagCommandTaskTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun computesTransitiveDependentsForChangedModule() {
        val projectDir = setupProject(
            modulePaths = listOf(":core", ":feature", ":app"),
            moduleDependencies = mapOf(
                ":feature" to listOf(":core"),
                ":app" to listOf(":feature")
            )
        )

        commitFeatureChange(projectDir, "core/src.txt", "core changed")
        val result = runDagCommand(projectDir)

        assertEquals(TaskOutcome.SUCCESS, result.task(":dag-command")?.outcome)
        assertEquals(setOf(":core", ":feature", ":app"), readAffectedModules(projectDir))
    }

    @Test
    fun writesEmptyListWhenNoModuleChanges() {
        val projectDir = setupProject(modulePaths = listOf(":core"))
        val result = runDagCommand(projectDir)

        assertEquals(TaskOutcome.SUCCESS, result.task(":dag-command")?.outcome)
        assertEquals(emptySet(), readAffectedModules(projectDir))
    }

    @Test
    fun ignoresRootOnlyChanges() {
        val projectDir = setupProject(modulePaths = listOf(":core", ":feature"))

        commitFeatureChange(projectDir, "README.md", "documentation changed")
        val result = runDagCommand(projectDir)

        assertEquals(TaskOutcome.SUCCESS, result.task(":dag-command")?.outcome)
        assertEquals(emptySet(), readAffectedModules(projectDir))
    }

    @Test
    fun marksNestedModuleAndItsParentPrefixesLikeOriginalDagCommand() {
        val projectDir = setupProject(
            modulePaths = listOf(":parent", ":parent:child", ":consumer"),
            moduleDependencies = mapOf(":consumer" to listOf(":parent:child"))
        )

        commitFeatureChange(projectDir, "parent/child/src.txt", "nested module changed")
        val result = runDagCommand(projectDir)

        assertEquals(TaskOutcome.SUCCESS, result.task(":dag-command")?.outcome)
        val affectedModules = readAffectedModules(projectDir)
        assertEquals(setOf(":parent", ":parent:child", ":consumer"), affectedModules)
    }

    @Test
    fun handlesCyclicDependenciesWithoutDuplicates() {
        val projectDir = setupProject(
            modulePaths = listOf(":a", ":b"),
            moduleDependencies = mapOf(
                ":a" to listOf(":b"),
                ":b" to listOf(":a")
            )
        )

        commitFeatureChange(projectDir, "a/src.txt", "cycle changed")
        val result = runDagCommand(projectDir)

        assertEquals(TaskOutcome.SUCCESS, result.task(":dag-command")?.outcome)
        val outputFile = affectedModulesFile(projectDir)
        val rawJson = outputFile.readText().trim()
        val modules = parseModules(rawJson)
        assertEquals(setOf(":a", ":b"), modules.toSet())
        assertEquals(modules.toSet().size, modules.size, "Affected modules must not contain duplicates")
    }

    @Test
    fun removesStaleOutputWhenBaseBranchCannotBeResolved() {
        val projectDir = setupProject(modulePaths = listOf(":core"), defaultBranch = "origin/missing-branch")
        val outputFile = affectedModulesFile(projectDir)
        outputFile.parent.createDirectories()
        outputFile.writeText("[\":core\"]")

        val result = runDagCommand(projectDir)

        assertEquals(TaskOutcome.SUCCESS, result.task(":dag-command")?.outcome)
        assertFalse(outputFile.exists(), "Stale affected-modules output should be removed")
        assertTrue(result.output.contains("Falling back to running all tests."))
    }

    @Test
    fun marksAllModulesWhenGradleFolderChanges() {
        val projectDir = setupProject(
            modulePaths = listOf(":core", ":feature", ":app"),
            moduleDependencies = mapOf(
                ":feature" to listOf(":core"),
                ":app" to listOf(":feature")
            )
        )

        commitFeatureChange(projectDir, "gradle/dependency-updates.txt", "updated")
        val result = runDagCommand(projectDir)

        assertEquals(TaskOutcome.SUCCESS, result.task(":dag-command")?.outcome)
        assertEquals(setOf(":core", ":feature", ":app"), readAffectedModules(projectDir))
    }

    @Test
    fun marksAllModulesWhenBuildSrcChanges() {
        val projectDir = setupProject(
            modulePaths = listOf(":core", ":feature", ":app"),
            moduleDependencies = mapOf(
                ":feature" to listOf(":core"),
                ":app" to listOf(":feature")
            )
        )

        commitFeatureChange(projectDir, "buildSrc/src/main/kotlin/Foo.kt", "class Foo")
        val result = runDagCommand(projectDir)

        assertEquals(TaskOutcome.SUCCESS, result.task(":dag-command")?.outcome)
        assertEquals(setOf(":core", ":feature", ":app"), readAffectedModules(projectDir))
    }

    private fun setupProject(
        modulePaths: List<String>,
        moduleDependencies: Map<String, List<String>> = emptyMap(),
        defaultBranch: String = "develop"
    ): Path {
        val projectDir = Files.createTempDirectory(tempDir, "dag-task-")
        writeSettings(projectDir, modulePaths)
        writeRootBuild(projectDir, defaultBranch)
        writeModuleBuildFiles(projectDir, modulePaths, moduleDependencies)
        initializeGit(projectDir)
        return projectDir
    }

    private fun writeSettings(projectDir: Path, modulePaths: List<String>) {
        val includes = modulePaths.joinToString(", ") { "\"$it\"" }
        projectDir.resolve("settings.gradle").writeText(
            """
            rootProject.name = 'dag-task-test'
            include($includes)
            """.trimIndent() + "\n"
        )
    }

    private fun writeRootBuild(projectDir: Path, defaultBranch: String) {
        projectDir.resolve("build.gradle").writeText(
            """
            plugins {
                id 'scripts.testing'
            }

            tasks.named('dag-command') {
                defaultBranch = '$defaultBranch'
            }
            """.trimIndent() + "\n"
        )
    }

    private fun writeModuleBuildFiles(
        projectDir: Path,
        modulePaths: List<String>,
        moduleDependencies: Map<String, List<String>>
    ) {
        modulePaths.forEach { modulePath ->
            val moduleDir = projectDir.resolve(modulePath.removePrefix(":").replace(':', '/'))
            moduleDir.createDirectories()
            moduleDir.resolve("src.txt").writeText(modulePath)

            val dependencyStatements = moduleDependencies[modulePath].orEmpty()
                .joinToString("\n") { dependencyPath -> "    dag project('$dependencyPath')" }

            val buildFile = if (dependencyStatements.isBlank()) {
                "configurations { dag }\n"
            } else {
                """
                configurations { dag }
                dependencies {
                $dependencyStatements
                }
                """.trimIndent() + "\n"
            }
            moduleDir.resolve("build.gradle").writeText(buildFile)
        }
    }

    private fun initializeGit(projectDir: Path) {
        runCommand(projectDir, "git", "init")
        runCommand(projectDir, "git", "config", "user.email", "dag-command-test@wire.com")
        runCommand(projectDir, "git", "config", "user.name", "Dag Command Test")
        runCommand(projectDir, "git", "add", ".")
        runCommand(projectDir, "git", "commit", "-m", "initial")
        runCommand(projectDir, "git", "branch", "-M", "develop")
        runCommand(projectDir, "git", "checkout", "-b", "feature")
    }

    private fun commitFeatureChange(projectDir: Path, relativePath: String, content: String) {
        val targetFile = projectDir.resolve(relativePath)
        targetFile.parent?.createDirectories()
        targetFile.writeText(content)
        runCommand(projectDir, "git", "add", relativePath)
        runCommand(projectDir, "git", "commit", "-m", "change $relativePath")
    }

    private fun runDagCommand(projectDir: Path) = GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments("dag-command", "--stacktrace")
        .build()

    private fun affectedModulesFile(projectDir: Path): Path =
        projectDir.resolve("build/dag-command/affected-modules.json")

    private fun readAffectedModules(projectDir: Path): Set<String> {
        val outputFile = affectedModulesFile(projectDir)
        assertTrue(outputFile.exists(), "Expected affected-modules output at $outputFile")
        return parseModules(outputFile.readText()).toSet()
    }

    private fun parseModules(json: String): List<String> =
        Regex("\"([^\"]+)\"")
            .findAll(json)
            .map { it.groupValues[1] }
            .toList()

    private fun runCommand(workingDir: Path, vararg command: String) {
        val process = ProcessBuilder(*command)
            .directory(workingDir.toFile())
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        assertEquals(
            0,
            exitCode,
            "Command `${command.joinToString(" ")}` failed in $workingDir:\n$output"
        )
    }
}
