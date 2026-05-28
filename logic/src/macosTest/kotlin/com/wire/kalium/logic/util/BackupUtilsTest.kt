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

package com.wire.kalium.logic.util

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import okio.Buffer
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import okio.Source
import okio.buffer
import okio.use
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Integration tests for macOS BackupUtils implementation.
 * These tests also verify the generated ZIP can be inspected by the system unzip tool.
 */
class BackupUtilsTest {

    private val fileSystem = FileSystem.SYSTEM
    private val testDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "kalium-backup-test-${kotlin.random.Random.nextInt().toUInt()}"
    private val testKaliumFileSystem = RealKaliumFileSystem(testDir)

    init {
        fileSystem.createDirectories(testDir)
    }

    @AfterTest
    fun cleanup() {
        fileSystem.deleteRecursively(testDir, mustExist = false)
    }

    // region createCompressedFile

    @Test
    fun givenSingleFile_whenCreatingCompressedFile_thenReturnsRightWithSize() {
        val content = "hello backup world"
        val sources = listOf(content.toSource() to "test.txt")
        val outputBuffer = Buffer()

        val result = createCompressedFile(sources, outputBuffer)

        assertIs<Either.Right<Long>>(result)
        assertTrue(result.value > 0, "Compressed size should be positive")
        assertTrue(outputBuffer.size > 0, "Output buffer should contain data")
    }

    @Test
    fun givenMultipleFiles_whenCreatingCompressedFile_thenAllFilesAreIncluded() {
        val files = listOf(
            "content-a".toSource() to "file_a.txt",
            "content-b".toSource() to "file_b.json",
            "content-c".toSource() to "file_c.proto",
        )
        val zipPath = testDir / "multi.zip"

        val createResult = createCompressedFile(files, fileSystem.sink(zipPath))
        assertIs<Either.Right<Long>>(createResult)

        val entries = listZipEntries(zipPath)
        assertEquals(setOf("file_a.txt", "file_b.json", "file_c.proto"), entries.toSet())
    }

    @Test
    fun givenEmptyFileList_whenCreatingCompressedFile_thenReturnsLeft() {
        val outputBuffer = Buffer()

        val result = createCompressedFile(emptyList(), outputBuffer)

        assertIs<Either.Left<*>>(result)
    }

    // endregion

    // region extractCompressedFile

    @Test
    fun givenCompressedFile_whenExtractingAll_thenFilesAreExtracted() {
        val originalContent = "extract me please"
        val zipPath = testDir / "extract-all.zip"
        createCompressedFile(
            listOf(originalContent.toSource() to "payload.txt"),
            fileSystem.sink(zipPath)
        )

        val outputDir = testDir / "extracted-all"
        val result = extractCompressedFile(
            inputSource = fileSystem.source(zipPath),
            outputRootPath = outputDir,
            param = ExtractFilesParam.All,
            fileSystem = testKaliumFileSystem,
        )

        assertIs<Either.Right<Long>>(result)
        assertTrue(result.value > 0)

        val extractedContent = fileSystem.source(outputDir / "payload.txt").buffer().use { it.readUtf8() }
        assertEquals(originalContent, extractedContent)
    }

    @Test
    fun givenCompressedFileWithMultipleEntries_whenExtractingOnly_thenOnlyMatchingFilesAreExtracted() {
        val zipPath = testDir / "extract-only.zip"
        createCompressedFile(
            listOf(
                "wanted".toSource() to "keep.txt",
                "unwanted".toSource() to "skip.txt",
            ),
            fileSystem.sink(zipPath)
        )

        val outputDir = testDir / "extracted-only"
        val result = extractCompressedFile(
            inputSource = fileSystem.source(zipPath),
            outputRootPath = outputDir,
            param = ExtractFilesParam.Only("keep.txt"),
            fileSystem = testKaliumFileSystem,
        )

        assertIs<Either.Right<Long>>(result)
        assertTrue(fileSystem.exists(outputDir / "keep.txt"))
        assertTrue(!fileSystem.exists(outputDir / "skip.txt"))
    }

    @Test
    fun givenZip64StoredFixture_whenExtractingAll_thenFileIsExtracted() {
        val content = "zip64 stored content"
        val outputDir = testDir / "zip64-stored"

        val result = extractCompressedFile(
            inputSource = zip64StoredFixture("payload.txt", content.encodeToByteArray()),
            outputRootPath = outputDir,
            param = ExtractFilesParam.All,
            fileSystem = testKaliumFileSystem,
        )

        assertIs<Either.Right<Long>>(result)
        assertEquals(content.length.toLong(), result.value)
        val extractedContent = fileSystem.source(outputDir / "payload.txt").buffer().use { it.readUtf8() }
        assertEquals(content, extractedContent)
    }

    // endregion

    // region round-trip

    @Test
    fun givenFilesCompressedThenExtracted_whenCompared_thenContentMatches() {
        val files = mapOf(
            "users.json" to """[{"id":"1","name":"Alice"}]""",
            "conversations.json" to """[{"id":"c1","name":"General"}]""",
            "messages.proto" to "binary-ish-content-here",
        )

        val zipPath = testDir / "roundtrip.zip"
        val createResult = createCompressedFile(
            files.map { (name, content) -> content.toSource() to name },
            fileSystem.sink(zipPath)
        )
        assertIs<Either.Right<Long>>(createResult)

        val outputDir = testDir / "roundtrip-extracted"
        val extractResult = extractCompressedFile(
            inputSource = fileSystem.source(zipPath),
            outputRootPath = outputDir,
            param = ExtractFilesParam.All,
            fileSystem = testKaliumFileSystem,
        )
        assertIs<Either.Right<Long>>(extractResult)

        files.forEach { (name, expectedContent) ->
            val actual = fileSystem.source(outputDir / name).buffer().use { it.readUtf8() }
            assertEquals(expectedContent, actual, "Content mismatch for $name")
        }
    }

    @Test
    fun givenLargeFile_whenRoundTripping_thenContentMatchesAndSystemUnzipAccepts() {
        val largeContent = ByteArray(LARGE_TEST_SIZE_BYTES) { (it % 251).toByte() }
        val zipPath = testDir / "large.zip"

        val createResult = createCompressedFile(
            listOf(largeContent.toSource() to "large.bin"),
            fileSystem.sink(zipPath)
        )
        assertIs<Either.Right<Long>>(createResult)

        val entries = listZipEntries(zipPath)
        assertEquals(listOf("large.bin"), entries)

        val outputDir = testDir / "large-extracted"
        val extractResult = extractCompressedFile(
            inputSource = fileSystem.source(zipPath),
            outputRootPath = outputDir,
            param = ExtractFilesParam.All,
            fileSystem = testKaliumFileSystem,
        )
        assertIs<Either.Right<Long>>(extractResult)
        assertEquals(LARGE_TEST_SIZE_BYTES.toLong(), extractResult.value)

        val extractedBytes = fileSystem.source(outputDir / "large.bin").buffer().use { it.readByteArray() }
        assertEquals(LARGE_TEST_SIZE_BYTES, extractedBytes.size)
        assertTrue(extractedBytes.contentEquals(largeContent), "Large round-trip content mismatch")
    }

    // endregion

    // region checkIfCompressedFileContainsFileTypes

    @Test
    fun givenZipWithMixedTypes_whenChecking_thenReturnsCorrectMap() {
        val zipPath = testDir / "filetypes.zip"
        createCompressedFile(
            listOf(
                "a".toSource() to "data.json",
                "b".toSource() to "data.proto",
            ),
            fileSystem.sink(zipPath)
        )

        val result = checkIfCompressedFileContainsFileTypes(
            compressedFilePath = zipPath,
            fileSystem = testKaliumFileSystem,
            expectedFileExtensions = listOf("json", "proto", "txt"),
        )

        assertIs<Either.Right<Map<String, Boolean>>>(result)
        assertEquals(true, result.value["json"])
        assertEquals(true, result.value["proto"])
        assertEquals(false, result.value["txt"])
    }

    @Test
    fun givenNonExistentFile_whenChecking_thenReturnsDataNotFound() {
        val result = checkIfCompressedFileContainsFileTypes(
            compressedFilePath = testDir / "does-not-exist.zip",
            fileSystem = testKaliumFileSystem,
            expectedFileExtensions = listOf("json"),
        )

        assertIs<Either.Left<StorageFailure.DataNotFound>>(result)
    }

    // endregion

    // region helpers

    private fun String.toSource(): Source {
        val buffer = Buffer()
        buffer.writeUtf8(this)
        return buffer
    }

    private fun ByteArray.toSource(): Source {
        val buffer = Buffer()
        buffer.write(this)
        return buffer
    }

    private fun listZipEntries(zipPath: Path): List<String> {
        val result = execCommand(args = listOf("/usr/bin/unzip", "-Z1", zipPath.toString()))
        return result.stdout.lines().filter { it.isNotBlank() }
    }

    private fun zip64StoredFixture(fileName: String, content: ByteArray): Source {
        val fileNameBytes = fileName.encodeToByteArray()
        val extra = Buffer()
            .writeShortLe(ZIP64_EXTRA_FIELD_ID)
            .writeShortLe(Long.SIZE_BYTES * 2)
            .writeLongLe(content.size.toLong())
            .writeLongLe(content.size.toLong())
            .readByteArray()

        return Buffer()
            .writeIntLe(LOCAL_FILE_HEADER_SIGNATURE)
            .writeShortLe(ZIP64_VERSION_NEEDED)
            .writeShortLe(GENERAL_PURPOSE_UTF8_FLAG)
            .writeShortLe(COMPRESSION_METHOD_STORED)
            .writeShortLe(0)
            .writeShortLe(0)
            .writeIntLe(ZIP64_STORED_CONTENT_CRC)
            .writeIntLe(ZIP32_MAX_FIELD)
            .writeIntLe(ZIP32_MAX_FIELD)
            .writeShortLe(fileNameBytes.size)
            .writeShortLe(extra.size)
            .write(fileNameBytes)
            .write(extra)
            .write(content)
            .writeIntLe(CENTRAL_DIRECTORY_HEADER_SIGNATURE)
    }

    /**
     * A KaliumFileSystem backed by the real filesystem for integration testing.
     * Required because the test verifies interoperability with the system unzip tool.
     */
    private class RealKaliumFileSystem(private val rootDir: Path) : KaliumFileSystem {
        private val fs = FileSystem.SYSTEM

        override val rootCachePath: Path = rootDir / "cache"
        override val rootDBPath: Path = rootDir / "db"

        init {
            fs.createDirectories(rootCachePath)
            fs.createDirectories(rootDBPath)
        }

        override fun sink(outputPath: Path, mustCreate: Boolean): Sink = fs.sink(outputPath, mustCreate)
        override fun source(inputPath: Path): Source = fs.source(inputPath)
        override fun createDirectories(dir: Path) = fs.createDirectories(dir)
        override fun createDirectory(dir: Path, mustCreate: Boolean) = fs.createDirectory(dir, mustCreate)
        override fun delete(path: Path, mustExist: Boolean) = fs.delete(path, mustExist)
        override fun deleteContents(dir: Path, mustExist: Boolean) = fs.deleteRecursively(dir, mustExist)
        override fun exists(path: Path): Boolean = fs.exists(path)
        override fun copy(sourcePath: Path, targetPath: Path) = fs.copy(sourcePath, targetPath)

        override fun tempFilePath(pathString: String?): Path {
            val name = pathString ?: "temp_${kotlin.random.Random.nextInt().toUInt()}"
            return rootCachePath / name
        }

        override fun providePersistentAssetPath(assetName: String): Path = rootDir / "assets" / assetName
        override suspend fun readByteArray(inputPath: Path): ByteArray =
            fs.source(inputPath).buffer().use { it.readByteArray() }

        override suspend fun writeData(outputSink: Sink, dataSource: Source): Long =
            outputSink.buffer().use { it.writeAll(dataSource) }

        override fun selfUserAvatarPath(): Path = providePersistentAssetPath("self_avatar.jpg")
        override suspend fun listDirectories(dir: Path): List<Path> = fs.list(dir)
        override fun size(path: Path): Long? = fs.metadata(path).size
    }

    // endregion

    private companion object {
        const val LARGE_TEST_SIZE_BYTES = 5 * 1024 * 1024
        const val LOCAL_FILE_HEADER_SIGNATURE = 0x04034b50
        const val CENTRAL_DIRECTORY_HEADER_SIGNATURE = 0x02014b50
        const val GENERAL_PURPOSE_UTF8_FLAG = 0x0800
        const val COMPRESSION_METHOD_STORED = 0
        const val ZIP64_VERSION_NEEDED = 45
        const val ZIP64_EXTRA_FIELD_ID = 0x0001
        const val ZIP32_MAX_FIELD = -1
        const val ZIP64_STORED_CONTENT_CRC = 0x64dad28d
    }
}
