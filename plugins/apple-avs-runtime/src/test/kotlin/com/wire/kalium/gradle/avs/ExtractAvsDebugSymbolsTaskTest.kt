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
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ExtractAvsDebugSymbolsTaskTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun givenVerifiedArchive_whenExtractingDebugSymbols_thenCopiesOnlyDsymEntries() {
        val archive = createArchive(
            mapOf(
                "avs.xcframework/ios-arm64/dSYMs/avs.framework.dSYM/Contents/Resources/DWARF/avs" to
                    "ios symbols",
                "avs.xcframework/macos-arm64_x86_64/dSYMs/avs.framework.dSYM/Contents/Resources/DWARF/avs" to
                    "macos symbols",
                "avs.xcframework/ios-arm64/avs.framework/avs" to "runtime executable",
                "unrelated.txt" to "unrelated",
            )
        )
        val task = createTask(archive, archive.sha256())

        task.extractVerifiedDebugSymbols()

        val output = task.outputDirectory.get().asFile.toPath()
        assertEquals(
            "ios symbols",
            output.resolve(
                "avs.xcframework/ios-arm64/dSYMs/avs.framework.dSYM/Contents/Resources/DWARF/avs"
            ).readText(),
        )
        assertEquals(
            "macos symbols",
            output.resolve(
                "avs.xcframework/macos-arm64_x86_64/dSYMs/avs.framework.dSYM/Contents/Resources/DWARF/avs"
            ).readText(),
        )
        assertFalse(output.resolve("avs.xcframework/ios-arm64/avs.framework/avs").exists())
        assertFalse(output.resolve("unrelated.txt").exists())
    }

    @Test
    fun givenCachedArchiveTamperedAfterPreparation_whenExtractingDebugSymbols_thenRejectsAndDeletesIt() {
        val archive = createArchive(
            mapOf("avs.xcframework/ios-arm64/dSYMs/avs.dSYM" to "symbols")
        )
        val task = createTask(archive, "0".repeat(64))

        val failure = assertFailsWith<GradleException> {
            task.extractVerifiedDebugSymbols()
        }

        assertTrue(failure.message.orEmpty().contains("Discarded the cached"))
        assertFalse(archive.exists())
        assertFalse(task.outputDirectory.get().asFile.exists())
    }

    private fun createTask(archive: Path, checksum: String): ExtractAvsDebugSymbolsTask {
        val projectDirectory = temporaryDirectory.resolve("project").apply { createDirectories() }
        val project = ProjectBuilder.builder().withProjectDir(projectDirectory.toFile()).build()
        return project.tasks.register(
            "extractAvsDebugSymbolsForTest",
            ExtractAvsDebugSymbolsTask::class.java,
        ).apply {
            configure {
                cachedArchive.set(archive.toFile())
                expectedSha256.set(checksum)
                outputDirectory.set(temporaryDirectory.resolve("debug-symbols").toFile())
            }
        }.get()
    }

    private fun createArchive(entries: Map<String, String>): Path =
        temporaryDirectory.resolve("avs.xcframework.zip").apply {
            ZipOutputStream(toFile().outputStream()).use { output ->
                entries.forEach { (name, contents) ->
                    output.putNextEntry(ZipEntry(name))
                    output.write(contents.encodeToByteArray())
                    output.closeEntry()
                }
            }
        }
}

private fun Path.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(readBytes())
    return digest.joinToString("") { byte -> "%02x".format(byte) }
}
