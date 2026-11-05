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

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeneratedSourcesTaskTest {
    @TempDir
    lateinit var projectDir: Path

    @Test
    fun generatedConfigReusesConfigurationAndOutputsAndTracksPolicyChanges() {
        fixture(
            """
            tasks.register('generateConfig', GenerateKotlinSourceTask) {
                outputDirectory.set(layout.buildDirectory.dir('generated/config'))
                relativePath.set('example/Config.kt')
                source.set(providers.gradleProperty('policy').map { 'val policy = "' + it + '"' })
            }
            """.trimIndent()
        )
        val output = projectDir.resolve("build/generated/config/example/Config.kt")
        val first = run("generateConfig", "-Ppolicy=LOCAL")
        assertEquals(TaskOutcome.SUCCESS, first.task(":generateConfig")?.outcome)
        assertEquals("val policy = \"LOCAL\"", output.readText())

        val second = run("generateConfig", "-Ppolicy=LOCAL")
        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":generateConfig")?.outcome)
        assertTrue(second.output.contains("Reusing configuration cache."))

        output.parent.parent.toFile().deleteRecursively()
        val restored = run("generateConfig", "-Ppolicy=LOCAL")
        assertEquals(TaskOutcome.FROM_CACHE, restored.task(":generateConfig")?.outcome)
        assertEquals("val policy = \"LOCAL\"", output.readText())

        val changed = run("generateConfig", "-Ppolicy=GLOBAL")
        assertEquals(TaskOutcome.SUCCESS, changed.task(":generateConfig")?.outcome)
        assertEquals("val policy = \"GLOBAL\"", output.readText())
    }

    @Test
    fun protobufPreparationPreservesProducerOutputAndRemovesObsoleteSources() {
        fixture(
            """
            tasks.register('prepareSources', PrepareProtobufSourcesTask) {
                sourceDirectory.set(layout.projectDirectory.dir('producer'))
                outputDirectory.set(layout.buildDirectory.dir('generated/protobuf'))
                fileAnnotation.set('@file:Suppress("UNNECESSARY_NOT_NULL_ASSERTION")')
            }
            """.trimIndent()
        )
        val source = projectDir.resolve("producer/pbandk/Message.kt")
        source.parent.createDirectories()
        source.writeText("class Message\n")
        val output = projectDir.resolve("build/generated/protobuf/pbandk/Message.kt")
        run("prepareSources")
        assertEquals("class Message\n", source.readText())
        assertEquals(
            "@file:Suppress(\"UNNECESSARY_NOT_NULL_ASSERTION\")\nclass Message\n",
            output.readText()
        )
        val unchanged = run("prepareSources")
        assertEquals(TaskOutcome.UP_TO_DATE, unchanged.task(":prepareSources")?.outcome)
        assertTrue(unchanged.output.contains("Reusing configuration cache."))

        output.parent.parent.toFile().deleteRecursively()
        assertEquals(TaskOutcome.FROM_CACHE, run("prepareSources").task(":prepareSources")?.outcome)
        assertTrue(output.toFile().exists())

        source.toFile().delete()
        projectDir.resolve("producer/pbandk/Replacement.kt").writeText("class Replacement\n")
        val changed = run("prepareSources")
        assertEquals(TaskOutcome.SUCCESS, changed.task(":prepareSources")?.outcome)
        assertFalse(output.toFile().exists())
        assertTrue(output.resolveSibling("Replacement.kt").toFile().exists())
    }

    private fun fixture(tasks: String) {
        projectDir.resolve("settings.gradle").writeText(
            "rootProject.name = 'generated-sources-test'\n" +
                "buildCache { local { directory = new File(rootDir, 'task-cache') } }\n"
        )
        projectDir.resolve("build.gradle").writeText(
            "plugins { id 'scripts.testing' }\n" + tasks + "\n"
        )
    }

    private fun run(vararg tasks: String) = GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments(*tasks, "--offline", "--configuration-cache", "--build-cache", "--stacktrace")
        .build()
}
