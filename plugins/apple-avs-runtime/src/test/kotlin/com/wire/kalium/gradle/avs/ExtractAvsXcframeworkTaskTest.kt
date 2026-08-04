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
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@EnabledOnOs(OS.MAC)
class ExtractAvsXcframeworkTaskTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun givenVerifiedArchive_whenExtracting_thenPublishesOnlyCompleteRuntimeSlices() {
        val archive = createAvsArchive()
        val task = createExtractionTask(archive)

        task.extractAndNormalize()

        val destination = task.cachedXcframework.get().asFile.toPath()
        AvsApplePlatform.entries.forEach { platform ->
            assertTrue(
                destination.resolve(
                    "avs.xcframework/${platform.xcframeworkSlice}/avs.framework/avs"
                ).exists()
            )
        }
        assertFalse(destination.resolve("untrusted/unexpected-file").exists())
        assertFalse(destination.resolve("avs.xcframework/ios-arm64/dSYMs/avs.dSYM").exists())
        assertTrue(isFrameworkCacheReady(destination.toFile(), archive.sha256()))

        val macosPlist = destination.resolve(
            "avs.xcframework/${AvsApplePlatform.MACOS.xcframeworkSlice}/avs.framework/Info.plist"
        ).readText()
        assertTrue(macosPlist.contains("MacOSX"))
        assertFalse(macosPlist.contains("MinimumOSVersion"))
    }

    @Test
    fun givenIncompleteArchive_whenExtracting_thenFailsWithoutPublishingCacheEntry() {
        val archive = createAvsArchive(
            omittedEntry =
            "avs.xcframework/${AvsApplePlatform.IOS_SIMULATOR.xcframeworkSlice}/avs.framework/avs"
        )
        val task = createExtractionTask(archive)

        val failure = assertFailsWith<GradleException> {
            task.extractAndNormalize()
        }

        assertTrue(failure.message.orEmpty().contains("is incomplete"))
        assertFalse(task.cachedXcframework.get().asFile.exists())
        assertTrue(
            task.cachedXcframework.get().asFile.toPath().parent
                .listDirectoryEntries("*.part-*")
                .isEmpty()
        )
    }

    @Test
    fun givenArchiveWithTraversalEntries_whenExtracting_thenRejectsArchiveWithoutWritingOutsideCache() {
        val archive = createAvsArchive(
            additionalEntries = mapOf(
                "../../outside-cache" to "escape attempt",
                "avs.xcframework/../../outside-xcframework" to "escape attempt",
                "untrusted/unexpected-file" to "unexpected",
            )
        )
        val task = createExtractionTask(archive)

        assertFailsWith<GradleException> {
            task.extractAndNormalize()
        }

        assertFalse(temporaryDirectory.resolve("outside-cache").exists())
        assertFalse(temporaryDirectory.resolve("outside-xcframework").exists())
        assertFalse(task.cachedXcframework.get().asFile.exists())
    }

    @Test
    fun givenCachedArchiveChangedAfterPreparation_whenExtracting_thenDeletesItAndRejectsExtraction() {
        val archive = createAvsArchive()
        val expectedChecksum = archive.sha256()
        val task = createExtractionTask(archive, expectedChecksum)
        archive.writeText("substituted after preparation")

        assertFailsWith<GradleException> {
            task.extractAndNormalize()
        }

        assertFalse(archive.exists())
        assertFalse(task.cachedXcframework.get().asFile.exists())
    }

    @Test
    fun givenConcurrentExtractionRequests_whenRunning_thenPublishesOneCompleteVerifiedCacheEntry() {
        val archive = createAvsArchive()
        val checksum = archive.sha256()
        val tasks = listOf(
            createExtractionTask(archive, checksum, taskIndex = 1),
            createExtractionTask(archive, checksum, taskIndex = 2),
        )
        val executor = Executors.newFixedThreadPool(tasks.size)

        try {
            executor.invokeAll(
                tasks.map { task -> Callable { task.extractAndNormalize() } }
            ).forEach { it.get() }
        } finally {
            executor.shutdownNow()
        }

        val destination = tasks.first().cachedXcframework.get().asFile
        assertTrue(isFrameworkCacheReady(destination, checksum))
        assertTrue(
            destination.toPath().parent.listDirectoryEntries("*.part-*").isEmpty()
        )
    }

    private fun createExtractionTask(
        archive: Path,
        expectedChecksum: String = archive.sha256(),
        taskIndex: Int = 0,
    ): ExtractAvsXcframeworkTask {
        val projectDirectory = temporaryDirectory.resolve("project-$taskIndex").apply {
            createDirectories()
        }
        val project = ProjectBuilder.builder()
            .withProjectDir(projectDirectory.toFile())
            .build()
        return project.tasks.register(
            "extractAvsXcframeworkForTest$taskIndex",
            ExtractAvsXcframeworkTask::class.java,
        ).apply {
            configure {
                expectedSha256.set(expectedChecksum)
                cachedArchive.set(archive.toFile())
                cachedXcframework.set(temporaryDirectory.resolve("framework-cache").toFile())
                outputMarker.set(temporaryDirectory.resolve("state/frameworks-ready").toFile())
            }
        }.get()
    }

    private fun createAvsArchive(
        omittedEntry: String? = null,
        additionalEntries: Map<String, String> = emptyMap(),
    ): Path {
        val archive = temporaryDirectory.resolve("avs.xcframework.zip")
        val entries = buildMap {
            put("avs.xcframework/Info.plist", validPlist())
            AvsApplePlatform.entries.forEach { platform ->
                val framework =
                    "avs.xcframework/${platform.xcframeworkSlice}/avs.framework"
                put("$framework/Info.plist", validPlist())
                put("$framework/avs", "${platform.name} executable")
            }
            put("avs.xcframework/ios-arm64/dSYMs/avs.dSYM", "debug symbols")
            putAll(additionalEntries)
            remove(omittedEntry)
        }
        ZipOutputStream(archive.toFile().outputStream()).use { output ->
            entries.forEach { (name, contents) ->
                output.putNextEntry(ZipEntry(name))
                output.write(contents.encodeToByteArray())
                output.closeEntry()
            }
        }
        return archive
    }

    private fun validPlist(): String =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
        <plist version="1.0">
        <dict>
            <key>CFBundleIdentifier</key>
            <string>com.wire.avs</string>
            <key>CFBundleSupportedPlatforms</key>
            <array><string>iPhoneOS</string></array>
            <key>MinimumOSVersion</key>
            <string>14.0</string>
        </dict>
        </plist>
        """.trimIndent()
}

private fun Path.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(readBytes())
    return digest.joinToString("") { byte -> "%02x".format(byte) }
}
