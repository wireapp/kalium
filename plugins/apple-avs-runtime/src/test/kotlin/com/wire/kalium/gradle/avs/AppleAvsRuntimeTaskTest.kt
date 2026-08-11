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

import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AppleAvsRuntimeTaskTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun givenExtractedFrameworkPathWithSpaces_whenGeneratingConfig_thenWritesAllPlatformSettings() {
        val project = newProject("config-project")
        val extracted = temporaryDirectory.resolve("cache with spaces/frameworks").apply {
            createDirectories()
        }
        val marker = temporaryDirectory.resolve("state/frameworks-ready").apply {
            parent.createDirectories()
            writeText("ready")
        }
        val output = temporaryDirectory.resolve("output/KaliumAvsRuntime.xcconfig")
        val task = project.tasks.register(
            "generateAvsXcodeConfigForTest",
            GenerateAvsXcodeConfigTask::class.java,
        ).apply {
            configure {
                extractedXcframework.set(extracted.toFile())
                extractedXcframeworkMarker.set(marker.toFile())
                outputFile.set(output.toFile())
                macosMinimumVersion.set("15.0")
            }
        }.get()

        task.generate()

        val config = output.readText()
        assertTrue(config.contains("KALIUM_AVS_XCFRAMEWORK_DIR = $extracted/avs.xcframework"))
        AvsApplePlatform.entries.forEach { platform ->
            assertTrue(
                config.contains(
                    "FRAMEWORK_SEARCH_PATHS[sdk=${platform.xcodePlatformPrefix}*] = " +
                        "\$(inherited) \"\$(KALIUM_AVS_XCFRAMEWORK_DIR)/${platform.xcframeworkSlice}\""
                )
            )
            assertTrue(
                config.contains(
                    "LD_RUNPATH_SEARCH_PATHS[sdk=${platform.xcodePlatformPrefix}*] = " +
                        "\$(inherited) ${platform.xcodeRunpathSearchPath}"
                )
            )
        }
        assertTrue(config.contains("OTHER_LDFLAGS = \$(inherited) -framework avs"))
        assertTrue(config.contains("MACOSX_DEPLOYMENT_TARGET[sdk=macosx*] = 15.0"))
    }

    @Test
    fun givenSupportedXcodePlatform_whenEmbedding_thenCopiesCorrectSliceAndRemovesStaleFiles() {
        val extracted = createExtractedFrameworks()
        val marker = temporaryDirectory.resolve("state/frameworks-ready").apply {
            parent.createDirectories()
            writeText("ready")
        }

        AvsApplePlatform.entries.forEachIndexed { index, platform ->
            val target = temporaryDirectory.resolve("target-$index")
            val stale = target.resolve("Frameworks/avs.framework/stale").apply {
                parent.createDirectories()
                writeText("stale")
            }
            val task = newProject("embed-project-$index").tasks.register(
                "embedAvsForXcodeForTest$index",
                EmbedAvsForXcodeTask::class.java,
            ).apply {
                configure {
                    extractedXcframework.set(extracted.toFile())
                    extractedXcframeworkMarker.set(marker.toFile())
                    platformName.set(platform.xcodePlatformPrefix)
                    targetBuildDirectory.set(target.toString())
                    frameworksFolderPath.set("Frameworks")
                    codeSignIdentity.set("identity-that-must-not-be-used")
                    codeSigningAllowed.set(false)
                }
            }.get()

            task.embedAndSign()

            val embedded = target.resolve("Frameworks/avs.framework")
            assertEquals(platform.name, embedded.resolve("slice-name").readText())
            assertFalse(stale.exists())
        }
    }

    @Test
    fun givenUnsupportedXcodePlatform_whenEmbedding_thenFailsBeforeCreatingDestination() {
        val extracted = createExtractedFrameworks()
        val marker = temporaryDirectory.resolve("state/frameworks-ready").apply {
            parent.createDirectories()
            writeText("ready")
        }
        val target = temporaryDirectory.resolve("target")
        val task = newProject("unsupported-platform-project").tasks.register(
            "embedAvsForUnsupportedXcodePlatform",
            EmbedAvsForXcodeTask::class.java,
        ).apply {
            configure {
                extractedXcframework.set(extracted.toFile())
                extractedXcframeworkMarker.set(marker.toFile())
                platformName.set("watchos")
                targetBuildDirectory.set(target.toString())
                frameworksFolderPath.set("Frameworks")
                codeSigningAllowed.set(false)
            }
        }.get()

        val failure = assertFailsWith<GradleException> {
            task.embedAndSign()
        }

        assertTrue(failure.message.orEmpty().contains("Unsupported Xcode PLATFORM_NAME"))
        assertFalse(target.exists())
    }

    @Test
    fun givenSigningIdentityWithShellCharacters_whenBuildingCommand_thenIdentityRemainsSingleArgument() {
        val identity = "Developer ID Application: Wire; touch /tmp/not-executed"
        val destination = temporaryDirectory.resolve("avs.framework").toFile()

        val command = codesignCommand(
            identity = identity,
            destination = destination,
            requiresSecureTimestamp = true,
        )

        assertEquals("/usr/bin/codesign", command.first())
        assertEquals(identity, command[command.indexOf("--sign") + 1])
        assertTrue("--timestamp" in command)
        assertEquals(destination.absolutePath, command.last())
    }

    @Test
    fun givenNonDistributionSigning_whenBuildingCommand_thenSecureTimestampIsNotRequested() {
        val command = codesignCommand(
            identity = "-",
            destination = temporaryDirectory.resolve("avs.framework").toFile(),
            requiresSecureTimestamp = false,
        )

        assertFalse("--timestamp" in command)
        assertTrue("--preserve-metadata=identifier" in command)
    }

    @Test
    fun givenSigningPlatformAndIdentity_whenSelectingTimestamp_thenOnlyMacosDeveloperIdRequiresIt() {
        assertTrue(
            requiresSecureTimestamp(
                AvsApplePlatform.MACOS,
                "Developer ID Application: Wire Swiss GmbH",
            )
        )
        assertFalse(
            requiresSecureTimestamp(
                AvsApplePlatform.IOS_DEVICE,
                "Developer ID Application: Wire Swiss GmbH",
            )
        )
        assertFalse(
            requiresSecureTimestamp(
                AvsApplePlatform.MACOS,
                "Apple Development: Wire Swiss GmbH",
            )
        )
    }

    @Test
    @EnabledOnOs(OS.MAC)
    fun givenAdHocSigningEnabled_whenEmbeddingMacosFramework_thenProducesStrictlyValidSignatureWithoutEntitlements() {
        val extracted = createSignableMacosFramework()
        val marker = temporaryDirectory.resolve("signing-state/frameworks-ready").apply {
            parent.createDirectories()
            writeText("ready")
        }
        val target = temporaryDirectory.resolve("signed-target")
        val task = newProject("signing-project").tasks.register(
            "embedAndSignAvsForXcodeForTest",
            EmbedAvsForXcodeTask::class.java,
        ).apply {
            configure {
                extractedXcframework.set(extracted.toFile())
                extractedXcframeworkMarker.set(marker.toFile())
                platformName.set(AvsApplePlatform.MACOS.xcodePlatformPrefix)
                targetBuildDirectory.set(target.toString())
                frameworksFolderPath.set("Frameworks")
                codeSignIdentity.set("-")
                codeSignIdentityName.set("Ad Hoc")
                codeSigningAllowed.set(true)
            }
        }.get()

        task.embedAndSign()

        val framework = target.resolve("Frameworks/avs.framework")
        val verification = ProcessBuilder(
            "/usr/bin/codesign",
            "--verify",
            "--strict",
            "--verbose=2",
            framework.toString(),
        ).redirectErrorStream(true).start()
        val verificationOutput = verification.inputStream.bufferedReader().readText()
        assertEquals(0, verification.waitFor(), verificationOutput)

        val entitlements = ProcessBuilder(
            "/usr/bin/codesign",
            "--display",
            "--entitlements",
            ":-",
            framework.toString(),
        ).redirectErrorStream(true).start()
        val entitlementsOutput = entitlements.inputStream.bufferedReader().readText()
        entitlements.waitFor()
        assertFalse(entitlementsOutput.contains("application-identifier"))
        assertFalse(entitlementsOutput.contains("com.apple.security"))
    }

    @Test
    fun givenKotlinAndXcodePlatformNames_whenResolving_thenMapsOnlySupportedAppleTargets() {
        AvsApplePlatform.entries.forEach { platform ->
            assertEquals(platform, AvsApplePlatform.forKotlinTarget(platform.kotlinTargetName))
            assertEquals(platform, AvsApplePlatform.forXcodePlatform("${platform.xcodePlatformPrefix}17.0"))
        }
        assertEquals(null, AvsApplePlatform.forKotlinTarget("iosX64"))
        assertEquals(null, AvsApplePlatform.forXcodePlatform("watchos"))
    }

    private fun newProject(name: String) = ProjectBuilder.builder()
        .withName(name)
        .withProjectDir(temporaryDirectory.resolve(name).apply { createDirectories() }.toFile())
        .build()

    private fun createExtractedFrameworks(): Path =
        temporaryDirectory.resolve("extracted").apply {
            AvsApplePlatform.entries.forEach { platform ->
                resolve("avs.xcframework/${platform.xcframeworkSlice}/avs.framework").apply {
                    createDirectories()
                    resolve("avs").writeText("binary")
                    resolve("Info.plist").writeText("plist")
                    resolve("slice-name").writeText(platform.name)
                }
            }
        }

    private fun createSignableMacosFramework(): Path =
        temporaryDirectory.resolve("signable-extracted").apply {
            resolve(
                "avs.xcframework/${AvsApplePlatform.MACOS.xcframeworkSlice}/avs.framework"
            ).apply {
                createDirectories()
                Files.copy(Path.of("/usr/bin/true"), resolve("avs"))
                resolve("Info.plist").writeText(
                    """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                    <plist version="1.0">
                    <dict>
                        <key>CFBundleExecutable</key>
                        <string>avs</string>
                        <key>CFBundleIdentifier</key>
                        <string>com.wire.avs.test</string>
                        <key>CFBundlePackageType</key>
                        <string>FMWK</string>
                    </dict>
                    </plist>
                    """.trimIndent()
                )
            }
        }
}
