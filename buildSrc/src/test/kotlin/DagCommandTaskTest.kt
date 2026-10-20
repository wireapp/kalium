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

    @Test
    fun listsAffectedIosTasksWithoutLaunchingANestedBuild() {
        val projectDir = setupProject(
            modulePaths = listOf(":core", ":feature", ":app", ":unrelated"),
            moduleDependencies = mapOf(
                ":feature" to listOf(":core"),
                ":app" to listOf(":feature")
            )
        )
        commitFeatureChange(projectDir, "core/src.txt", "core changed")

        val result = listIosTasks(projectDir)

        assertEquals(TaskOutcome.SUCCESS, result.task(":iOSOnlyAffectedTest")?.outcome)
        assertEquals(setOf(":core", ":feature", ":app"), readSelectedIosModules(projectDir))
        assertFalse(result.output.contains("test ran"))
    }

    @Test
    fun listsAllIosTasksForRootBuildChangesAndStillExcludesIgnoredModules() {
        val projectDir = setupProject(modulePaths = listOf(":core", ":feature", ":data:protobuf"))
        commitFeatureChange(projectDir, "gradle.properties", "org.gradle.parallel=true\n")

        listIosTasks(projectDir)

        assertEquals(setOf(":core", ":feature"), readSelectedIosModules(projectDir))
    }

    @Test
    fun clearsStaleSelectedTasksWhenNoTestsAreAffected() {
        val projectDir = setupProject(modulePaths = listOf(":core"))
        val selectedFile = projectDir.resolve("build/affected-tests/iOSOnlyAffectedTest.txt")
        selectedFile.parent.createDirectories()
        selectedFile.writeText(":core:iosSimulatorArm64Test\n")

        listIosTasks(projectDir)

        assertEquals("", selectedFile.readText())
    }

    @Test
    fun listsAllIosTasksWhenAffectedModuleDataIsUnavailable() {
        val projectDir = setupProject(
            modulePaths = listOf(":core", ":feature"),
            defaultBranch = "origin/missing-branch"
        )

        val result = listIosTasks(projectDir)

        assertEquals(setOf(":core", ":feature"), readSelectedIosModules(projectDir))
        assertTrue(result.output.contains("Running all tests because affected-modules data is unavailable."))
    }

    @Test
    fun iosShardsCoverEveryEligibleModuleExactlyOnceOnDevelop() {
        val modules = listOf(
            ":logic", ":data:persistence", ":data:network", ":core:cryptography",
            ":nse", ":share", ":domain:messaging:receiving", ":future:module", ":data:protobuf"
        )
        val projectDir = setupProject(modulePaths = modules)
        val expectedGroups = mapOf(
            "logic" to setOf(":logic"),
            "data" to setOf(":data:persistence", ":data:network", ":core:cryptography"),
            "receiving" to setOf(":domain:messaging:receiving"),
            "extensions" to setOf(":nse", ":share", ":future:module")
        )
        val selected = expectedGroups.flatMap { (shard, expectedModules) ->
            val result = listIosTasks(projectDir, "--all-modules", "--ios-shard=$shard")
            assertFalse(result.output.contains("test ran"))
            val selectedModules = readSelectedIosModules(projectDir)
            assertEquals(expectedModules, selectedModules, "Unexpected modules in $shard")
            selectedModules.toList()
        }

        assertEquals(modules.toSet() - ":data:protobuf", selected.toSet())
        assertEquals(selected.toSet().size, selected.size, "Each module must run in exactly one shard")
    }

    @Test
    fun iosShardsPreserveAffectedDependentsAndClearEmptySelections() {
        val projectDir = setupProject(
            modulePaths = listOf(":data:persistence", ":logic", ":domain:messaging:receiving", ":nse", ":share", ":unrelated"),
            moduleDependencies = mapOf(
                ":logic" to listOf(":data:persistence"),
                ":domain:messaging:receiving" to listOf(":data:persistence"),
                ":nse" to listOf(":domain:messaging:receiving")
            ),
            defaultBranch = "origin/develop"
        )
        commitFeatureChange(projectDir, "data/persistence/src.txt", "database changed")

        listIosTasks(projectDir, "--ios-shard=logic")
        assertEquals(setOf(":logic"), readSelectedIosModules(projectDir))
        listIosTasks(projectDir, "--ios-shard=data")
        assertEquals(setOf(":data:persistence"), readSelectedIosModules(projectDir))
        listIosTasks(projectDir, "--ios-shard=receiving")
        assertEquals(setOf(":domain:messaging:receiving"), readSelectedIosModules(projectDir))
        listIosTasks(projectDir, "--ios-shard=extensions")
        assertEquals(setOf(":nse"), readSelectedIosModules(projectDir))

        // A later extension-only change must not reuse this file's earlier selected tasks.
        runCommand(projectDir, "git", "update-ref", "refs/remotes/origin/develop", "HEAD")
        commitFeatureChange(projectDir, "share/src.txt", "share changed")
        listOf("logic", "data", "receiving").forEach { shard ->
            listIosTasks(projectDir, "--ios-shard=$shard")
            assertEquals(emptySet(), readSelectedIosModules(projectDir))
        }
    }

    @Test
    fun iosWorkflowAndFixtureChangesRunAllEligibleTests() {
        val projectDir = setupProject(
            modulePaths = listOf(":logic", ":nse", ":share", ":data:protobuf"),
            defaultBranch = "origin/develop"
        )
        commitFeatureChange(projectDir, ".github/workflows/gradle-ios-tests.yml", "workflow changed")
        listIosTasks(projectDir)
        assertEquals(setOf(":logic", ":nse", ":share"), readSelectedIosModules(projectDir))

        runCommand(projectDir, "git", "update-ref", "refs/remotes/origin/develop", "HEAD")
        commitFeatureChange(projectDir, "nse/scripts/x509_fixture_server.py", "fixture changed")
        listIosTasks(projectDir)
        assertEquals(setOf(":logic", ":nse", ":share"), readSelectedIosModules(projectDir))
    }

    @Test
    fun rejectsUnknownIosShardInsteadOfPassingWithNoTests() {
        val projectDir = setupProject(modulePaths = listOf(":logic"))
        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("iOSOnlyAffectedTest", "--list-only", "--ios-shard=typo")
            .buildAndFail()

        assertTrue(result.output.contains("Unknown iOS shard: typo"))
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
            moduleDir.resolve("build.gradle").writeText(
                buildFile + "\ntasks.register('iosSimulatorArm64Test') { doLast { println('test ran') } }\n"
            )
        }
    }

    private fun initializeGit(projectDir: Path) {
        runCommand(projectDir, "git", "init")
        runCommand(projectDir, "git", "config", "user.email", "dag-command-test@wire.com")
        runCommand(projectDir, "git", "config", "user.name", "Dag Command Test")
        runCommand(projectDir, "git", "add", ".")
        runCommand(projectDir, "git", "commit", "-m", "initial")
        runCommand(projectDir, "git", "branch", "-M", "develop")
        runCommand(projectDir, "git", "update-ref", "refs/remotes/origin/develop", "develop")
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

    private fun listIosTasks(projectDir: Path, vararg options: String) = GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments(listOf("iOSOnlyAffectedTest", "--list-only", "--stacktrace") + options)
        .build()

    private fun readSelectedIosModules(projectDir: Path): Set<String> =
        projectDir.resolve("build/affected-tests/iOSOnlyAffectedTest.txt")
            .readText()
            .lineSequence()
            .filter { it.isNotBlank() }
            .map { it.removeSuffix(":iosSimulatorArm64Test") }
            .toSet()

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
