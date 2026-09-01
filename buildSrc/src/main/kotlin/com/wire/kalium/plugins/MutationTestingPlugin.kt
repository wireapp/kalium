/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.register
import java.io.File

private const val MAX_MUTATION_THRESHOLD = 100

abstract class MutationTestingExtension {
    abstract val targetClasses: ListProperty<String>
    abstract val targetTests: ListProperty<String>
}

/**
 * Runs mutation tests for KMP common code on the JVM target.
 *
 * PIT mutates JVM bytecode, so common KMP code is exercised through the JVM target.
 */
class MutationTestingPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        val extension = extensions.create("mutationTesting", MutationTestingExtension::class.java).apply {
            targetClasses.convention(listOf("com.wire.kalium.*"))
            targetTests.convention(listOf("com.wire.kalium.*"))
        }
        val pitest = configurations.create("pitest") {
            isCanBeConsumed = false
            isCanBeResolved = true
            description = "PIT command-line mutation testing engine."
        }
        dependencies.add(pitest.name, library("pitest-commandLine"))

        val jvmTestClasspath = providers.provider {
            tasks.named("jvmTest", Test::class.java).get().classpath
        }
        val mainClasses = layout.buildDirectory.dir("classes/kotlin/jvm/main")
        val testClasses = layout.buildDirectory.dir("classes/kotlin/jvm/test")
        val reports = layout.buildDirectory.dir("reports/pitest")
        val parallelMutants = providers.gradleProperty("mutation.maxParallelMutants")
            .map { value ->
                value.toIntOrNull()?.takeIf { it > 0 }
                    ?: error("mutation.maxParallelMutants must be a positive integer")
            }
            .orElse(2)
        val mutationThreshold = providers.gradleProperty("mutation.threshold")
            .map { value ->
                value.toIntOrNull()?.takeIf { it in 0..MAX_MUTATION_THRESHOLD }
                    ?: error("mutation.threshold must be an integer from 0 to 100")
            }
            .orElse(0)
        val maxHeap = providers.gradleProperty("mutation.maxHeap").orElse("2g")
        val timeoutConstantMs = providers.gradleProperty("mutation.timeoutConstantMs")
            .map { value ->
                value.toIntOrNull()?.takeIf { it > 0 }
                    ?: error("mutation.timeoutConstantMs must be a positive integer")
            }
            .orElse(1_000)

        tasks.register<JavaExec>("mutationTest") {
            group = "verification"
            description = "Runs PIT against KMP common code on the JVM target."
            classpath(pitest)
            mainClass.set("org.pitest.mutationtest.commandline.MutationCoverageReport")
            workingDir(projectDir)
            maxHeapSize = maxHeap.get()

            dependsOn("compileKotlinJvm", "compileTestKotlinJvm")
            inputs.dir(mainClasses)
            inputs.dir(testClasses)
            inputs.files(jvmTestClasspath).withPropertyName("jvmTestClasspath")
            inputs.property("maxParallelMutants", parallelMutants)
            inputs.property("mutationThreshold", mutationThreshold)
            inputs.property("mutationMaxHeap", maxHeap)
            inputs.property("mutationTimeoutConstantMs", timeoutConstantMs)
            inputs.property("mutationTargetClasses", extension.targetClasses)
            inputs.property("mutationTargetTests", extension.targetTests)
            outputs.dir(reports)

            doFirst {
                configurePitestArguments(
                    PitestArguments(
                        testClasspath = jvmTestClasspath.get(),
                        mainClassesDirectory = mainClasses.get().asFile,
                        testClassesDirectory = testClasses.get().asFile,
                        reportsDirectory = reports.get().asFile,
                        sourceDirectories = mutationSourceDirectories(),
                        parallelMutants = parallelMutants.get(),
                        mutationThreshold = mutationThreshold.get(),
                        maxHeap = maxHeap.get(),
                        timeoutConstantMs = timeoutConstantMs.get(),
                        targetClasses = extension.targetClasses.get(),
                        targetTests = extension.targetTests.get(),
                    ),
                )
            }
        }
    }
}

private fun Project.mutationSourceDirectories(): List<File> = listOf(
    layout.projectDirectory.dir("src/commonMain/kotlin").asFile,
    layout.projectDirectory.dir("src/jvmMain/kotlin").asFile,
).filter(File::isDirectory)

private class PitestArguments(
    val testClasspath: FileCollection,
    val mainClassesDirectory: File,
    val testClassesDirectory: File,
    val reportsDirectory: File,
    val sourceDirectories: List<File>,
    val parallelMutants: Int,
    val mutationThreshold: Int,
    val maxHeap: String,
    val timeoutConstantMs: Int,
    val targetClasses: List<String>,
    val targetTests: List<String>,
)

private fun JavaExec.configurePitestArguments(arguments: PitestArguments) {
    val runtimeClasspath = (
        arguments.testClasspath.files +
            arguments.mainClassesDirectory +
            arguments.testClassesDirectory
        )
        .distinctBy(File::getAbsolutePath)
        .joinToString(",", transform = File::getAbsolutePath)

    setArgs(
        listOf(
            "--reportDir", arguments.reportsDirectory.absolutePath,
            "--targetClasses", arguments.targetClasses.joinToString(","),
            "--targetTests", arguments.targetTests.joinToString(","),
            "--sourceDirs", arguments.sourceDirectories.joinToString(",", transform = File::getAbsolutePath),
            "--classPath", runtimeClasspath,
            "--mutableCodePaths", arguments.mainClassesDirectory.absolutePath,
            "--outputFormats", "HTML,XML",
            "--timestampedReports", "false",
            "--threads", arguments.parallelMutants.toString(),
            "--mutationThreshold", arguments.mutationThreshold.toString(),
            "--jvmArgs", "-Xmx${arguments.maxHeap}",
            "--timeoutConst", arguments.timeoutConstantMs.toString(),
        ),
    )
}
