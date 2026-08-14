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

package com.wire.kalium.gradle.avs

import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AppleAvsRuntimePluginTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun givenPluginApplied_whenConfiguringProject_thenRegistersRuntimeTasksWithPinnedMetadata() {
        val projectDirectory = temporaryDirectory.resolve("registration-project").apply {
            createDirectories()
        }
        val project = ProjectBuilder.builder().withProjectDir(projectDirectory.toFile()).build()

        AppleAvsRuntimePlugin().apply(project)

        val prepare = project.tasks.named("prepareAvsXcframeworkArchive").get()
        val extract = project.tasks.named("extractAvsXcframework").get()
        val generate = project.tasks.named("generateAvsXcodeConfig").get()
        val embed = project.tasks.named("embedAvsForXcode").get()
        assertIs<PrepareAvsXcframeworkTask>(prepare)
        assertIs<ExtractAvsXcframeworkTask>(extract)
        assertIs<GenerateAvsXcodeConfigTask>(generate)
        assertIs<EmbedAvsForXcodeTask>(embed)
        assertIs<ExtractAvsDebugSymbolsTask>(
            project.tasks.named("extractAvsDebugSymbols").get()
        )
        assertEquals(AvsRuntimeArtifact.archiveUrl, prepare.archiveUrl.get())
        assertEquals(AvsRuntimeArtifact.archiveSha256, prepare.expectedSha256.get())
        assertEquals(AvsRuntimeArtifact.archiveSha256, extract.expectedSha256.get())
        assertEquals(AvsRuntimeArtifact.macosMinimumVersion, generate.macosMinimumVersion.get())
    }

    @Test
    fun givenAppleTargetsAndRemovedDisableProperty_whenConfiguring_thenEveryBinaryStillLinksVerifiedAvs() {
        val projectDirectory = temporaryDirectory.resolve("functional-project").apply {
            createDirectories()
        }
        projectDirectory.resolve("settings.gradle.kts").writeText(
            "rootProject.name = \"apple-avs-functional-test\"\n"
        )
        projectDirectory.resolve("build.gradle.kts").writeText(FUNCTIONAL_BUILD_SCRIPT)

        val result = GradleRunner.create()
            .withProjectDir(projectDirectory.toFile())
            .withPluginClasspath()
            .withArguments(
                "inspectAppleAvsWiring",
                "-Pkalium.disableAppleAvs=true",
                "--stacktrace",
            )
            .build()

        assertLinkWiring(result.output, "IosArm64", AvsApplePlatform.IOS_DEVICE)
        assertLinkWiring(result.output, "IosSimulatorArm64", AvsApplePlatform.IOS_SIMULATOR)
        assertLinkWiring(result.output, "MacosArm64", AvsApplePlatform.MACOS)
        assertTrue(result.output.contains("osVersionMin.macos_arm64=15.0"))
        assertTrue(result.output.contains("AVS_STAGE:linkDebugExecutableMacosArm64:stageAvsFor"))
        assertTrue(result.output.contains("AVS_STAGE:linkDebugTestIosSimulatorArm64:stageAvsFor"))
        assertTrue(
            result.output.lines().any { line -> line == "AVS_STAGE:linkDebugTestIosArm64:" }
        )
    }

    @Test
    fun givenPublishedAvsDependency_whenLoadingRuntimeMetadata_thenVersionAndTrustInputsRemainConsistent() {
        val versionCatalog = Path.of(
            checkNotNull(System.getProperty("kalium.versionCatalog"))
        ).readText()
        val catalogAvsVersion = checkNotNull(
            Regex("(?m)^avs\\s*=\\s*\"([^\"]+)\"")
                .find(versionCatalog)
                ?.groupValues
                ?.get(1)
        )

        assertEquals(catalogAvsVersion, AvsRuntimeArtifact.version)
        assertEquals(
            "https://github.com/wireapp/wire-avs/releases/download/" +
                "$catalogAvsVersion/avs.xcframework.zip",
            AvsRuntimeArtifact.archiveUrl,
        )
        assertTrue(AvsRuntimeArtifact.archiveSha256.matches(Regex("[0-9a-f]{64}")))
        assertTrue(AvsRuntimeArtifact.macosMinimumVersion.matches(Regex("[0-9]+\\.[0-9]+")))
    }

    private fun assertLinkWiring(
        output: String,
        taskTargetName: String,
        platform: AvsApplePlatform,
    ) {
        assertTrue(
            output.lines().any { line ->
                line.startsWith("AVS_LINK:linkDebugFramework$taskTargetName:") &&
                    line.contains("/${platform.xcframeworkSlice}|-framework|avs")
            }
        )
        assertTrue(
            output.lines().any { line ->
                line.startsWith("AVS_DEP:linkDebugFramework$taskTargetName:") &&
                    line.contains("extractAvsXcframework")
            }
        )
    }

    private companion object {
        val FUNCTIONAL_BUILD_SCRIPT =
            """
            import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
            import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink

            plugins {
                id("com.wire.kalium.apple-avs-runtime")
            }

            apply(plugin = "org.jetbrains.kotlin.multiplatform")

            extensions.configure<KotlinMultiplatformExtension> {
                iosArm64 {
                    binaries.framework()
                }
                iosSimulatorArm64 {
                    binaries.framework()
                }
                macosArm64 {
                    binaries.framework()
                    binaries.executable()
                }
            }

            tasks.register("inspectAppleAvsWiring") {
                doLast {
                    tasks.withType<KotlinNativeLink>()
                        .sortedBy { it.name }
                        .forEach { linkTask ->
                            println("AVS_LINK:${'$'}{linkTask.name}:${'$'}{linkTask.linkerOpts.joinToString("|")}")
                            println(
                                "AVS_ARGS:${'$'}{linkTask.name}:" +
                                    linkTask.toolOptions.freeCompilerArgs.get().joinToString("|")
                            )
                            println(
                                "AVS_DEP:${'$'}{linkTask.name}:" +
                                    linkTask.dependsOn.joinToString("|")
                            )
                            println(
                                "AVS_STAGE:${'$'}{linkTask.name}:" +
                                    linkTask.finalizedBy.getDependencies(linkTask)
                                        .joinToString("|") { it.name }
                            )
                        }
                }
            }
            """.trimIndent()
    }
}
