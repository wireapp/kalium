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
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/**
 * Gradle 9 compatible replacement for the external dag-command plugin.
 * It writes the same affected-modules output used by [OnlyAffectedTestTask].
 */
open class DagCommandTask : DefaultTask() {

    @Input
    var defaultBranch: String = "origin/develop"

    init {
        group = "verification"
        description = "Computes affected modules and writes build/dag-command/affected-modules.json"
    }

    @TaskAction
    fun computeAffectedModules() {
        val outputFile = project.layout.buildDirectory.file(OUTPUT_FILE).get().asFile
        val changedModules = changedModules() ?: run {
            outputFile.delete()
            return
        }
        val affectedModules = expandToDependents(changedModules)

        outputFile.parentFile.mkdirs()
        outputFile.writeText(
            affectedModules
                .sorted()
                .joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
        )

        println("Default branch: $defaultBranch")
        println("Output type: json")
        println("Output path: ${project.layout.buildDirectory.get().asFile.absolutePath}")
        println("Changed modules: ${changedModules.size}, affected modules: ${affectedModules.size}")
    }

    private fun changedModules(): Set<String>? {
        val process = ProcessBuilder(
            "git",
            "-C",
            project.rootDir.absolutePath,
            "diff",
            defaultBranch,
            "--dirstat=files,0"
        )
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        if (process.waitFor() != 0) {
            println("Unable to resolve changed files from git. Falling back to running all tests.")
            return null
        }

        val allModules = project.subprojects.map { it.path }.toSet()
        val parsedModules = output
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .flatMap { parseModuleCandidates(it).asSequence() }
            .toSet()

        return if (parsedModules.contains(BUILD_SRC) || parsedModules.contains(GRADLE)) {
            allModules
        } else {
            parsedModules.filter(allModules::contains).toSet()
        }
    }

    private fun parseModuleCandidates(dirstatLine: String): Set<String> {
        val fullPath = dirstatLine
            .trimStart()
            .split(" ", limit = 2)
            .getOrNull(1)
            ?.trim()
            .orEmpty()

        val words = fullPath.split("/")
            .takeWhile { it != "src" }
            .filter { it.isNotEmpty() }

        return words.fold(emptySet()) { acc, word ->
            if (acc.isEmpty()) {
                setOf(":$word")
            } else {
                val lastWord = acc.last()
                acc + "$lastWord:$word"
            }
        }
    }

    private fun expandToDependents(changedModules: Set<String>): Set<String> {
        if (changedModules.isEmpty()) return emptySet()

        val reverseGraph = mutableMapOf<String, MutableSet<String>>()
        project.subprojects.forEach { module ->
            val dependencies = module.configurations.flatMap { configuration ->
                configuration.dependencies
                    .withType(ProjectDependency::class.java)
                    .map { dependency -> dependency.path }
            }.toSet()

            dependencies.forEach { dependencyPath ->
                reverseGraph.getOrPut(dependencyPath) { mutableSetOf() }.add(module.path)
            }
        }

        val visited = changedModules.toMutableSet()
        val queue = ArrayDeque(changedModules)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            reverseGraph[current].orEmpty().forEach { dependent ->
                if (visited.add(dependent)) {
                    queue.add(dependent)
                }
            }
        }
        return visited
    }

    private companion object {
        const val OUTPUT_FILE = "dag-command/affected-modules.json"
        const val BUILD_SRC = ":buildSrc"
        const val GRADLE = ":gradle"
    }
}
