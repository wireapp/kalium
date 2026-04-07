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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.data.web.KtxWebSerializer
import kotlinx.serialization.decodeFromString
import okio.BufferedSource
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import okio.Source
import okio.buffer
import okio.use
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

@Suppress("TooGenericExceptionCaught", "NestedBlockDepth")
internal actual fun createCompressedFile(files: List<Pair<Source, String>>, outputSink: Sink): Either<CoreFailure, Long> = try {
    val fileSystem = FileSystem.SYSTEM
    val tempDir = appCacheTempDir("kalium-backup-zip-${kotlin.random.Random.nextInt().toUInt()}")
    fileSystem.createDirectories(tempDir)

    try {
        val stagedFiles = stageSourceFiles(files, tempDir, fileSystem)

        val zipPath = tempDir / "backup.zip"
        val zipResult = execCommand(
            args = listOf("/usr/bin/zip", "-q", "-j", zipPath.toString()) + stagedFiles.map { it.toString() },
            workingDirectory = tempDir,
        )
        ensureSuccess(zipResult, "compress the provided files")

        val compressedSize = fileSystem.metadata(zipPath).size ?: 0L
        fileSystem.source(zipPath).buffer().use { zipSource ->
            outputSink.buffer().use { bufferedSink ->
                bufferedSink.writeAll(zipSource)
            }
        }
        Either.Right(compressedSize)
    } finally {
        fileSystem.deleteRecursively(tempDir, false)
    }
} catch (e: Exception) {
    Either.Left(StorageFailure.Generic(RuntimeException("There was an error trying to compress the provided files", e)))
}

private fun stageSourceFiles(files: List<Pair<Source, String>>, tempDir: Path, fileSystem: FileSystem): List<Path> =
    files.map { (source, fileName) ->
        val sanitizedFileName = sanitizeFileName(fileName)
        val stagedPath = tempDir / sanitizedFileName
        source.buffer().use { bufferedSource ->
            fileSystem.sink(stagedPath).buffer().use { sink ->
                sink.writeAll(bufferedSource)
            }
        }
        stagedPath
    }

@Suppress("TooGenericExceptionCaught", "NestedBlockDepth")
internal actual fun extractCompressedFile(
    inputSource: Source,
    outputRootPath: Path,
    param: ExtractFilesParam,
    fileSystem: KaliumFileSystem
): Either<CoreFailure, Long> = try {
    val systemFileSystem = FileSystem.SYSTEM
    val tempDir = fileSystem.tempFilePath("backup-unzip-${kotlin.random.Random.nextInt().toUInt()}")
    systemFileSystem.createDirectories(tempDir)

    try {
        val zipPath = writeSourceToFile(inputSource, tempDir / "archive.zip", systemFileSystem)
        extractEntries(zipPath, outputRootPath, param, fileSystem)
    } finally {
        systemFileSystem.deleteRecursively(tempDir, false)
    }
} catch (e: Exception) {
    kaliumLogger.e("Error extracting compressed backup file", e)
    Either.Left(StorageFailure.Generic(RuntimeException("There was an error trying to extract the provided compressed file", e)))
}

private fun writeSourceToFile(source: Source, path: Path, fileSystem: FileSystem): Path {
    source.buffer().use { bufferedSource ->
        fileSystem.sink(path).buffer().use { sink ->
            sink.writeAll(bufferedSource)
        }
    }
    return path
}

private fun extractEntries(
    zipPath: Path,
    outputRootPath: Path,
    param: ExtractFilesParam,
    fileSystem: KaliumFileSystem
): Either<CoreFailure, Long> {
    val entries = listArchiveEntries(zipPath)
    val targetEntries = when (param) {
        ExtractFilesParam.All -> entries
        is ExtractFilesParam.Only -> entries.filter { it in param.files }
    }

    if (targetEntries.any(::isInvalidEntryPathDestination)) {
        throw IllegalArgumentException("Archive contains invalid entry path")
    }

    fileSystem.createDirectories(outputRootPath)
    if (targetEntries.isNotEmpty()) {
        val unzipArgs = buildUnzipArgs(zipPath, outputRootPath, param, targetEntries)
        ensureSuccess(execCommand(args = unzipArgs), "extract the provided compressed file")
    }

    val totalExtractedSize = targetEntries.sumOf { entryName ->
        val extractedPath = outputRootPath / entryName
        if (fileSystem.exists(extractedPath)) {
            FileSystem.SYSTEM.metadata(extractedPath).size ?: 0L
        } else {
            0L
        }
    }
    return Either.Right(totalExtractedSize)
}

private fun buildUnzipArgs(
    zipPath: Path,
    outputRootPath: Path,
    param: ExtractFilesParam,
    targetEntries: List<String>
): List<String> = buildList {
    add("/usr/bin/unzip")
    add("-qq")
    add("-o")
    add(zipPath.toString())
    if (param is ExtractFilesParam.Only) addAll(targetEntries)
    add("-d")
    add(outputRootPath.toString())
}

@Suppress("TooGenericExceptionCaught")
internal actual fun checkIfCompressedFileContainsFileTypes(
    compressedFilePath: Path,
    fileSystem: KaliumFileSystem,
    expectedFileExtensions: List<String>
): Either<CoreFailure, Map<String, Boolean>> =
    try {
        if (!fileSystem.exists(compressedFilePath)) {
            return Either.Left(StorageFailure.DataNotFound)
        }

        val archiveEntries = listArchiveEntries(compressedFilePath)
        val foundExtensions = archiveEntries.map { it.substringAfterLast('.', "") }.toSet()
        Either.Right(expectedFileExtensions.associateWith { it in foundExtensions })
    } catch (e: Exception) {
        Either.Left(StorageFailure.Generic(RuntimeException("There was an error trying to validate the provided compressed file", e)))
    }

internal actual inline fun <reified T> decodeBufferSequence(bufferedSource: BufferedSource): Sequence<T> {
    val jsonString = bufferedSource.readUtf8()
    return KtxWebSerializer.json.decodeFromString<List<T>>(jsonString).asSequence()
}

private fun listArchiveEntries(zipPath: Path): List<String> {
    val result = execCommand(
        args = listOf("/usr/bin/unzip", "-Z1", zipPath.toString()),
    )
    ensureSuccess(result, "list compressed file entries")
    return result.stdout
        .lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toList()
}

private fun sanitizeFileName(fileName: String): String {
    require(fileName.isNotBlank()) { "File name cannot be blank" }
    require(!isInvalidEntryPathDestination(fileName)) { "Invalid file name: $fileName" }
    return fileName.substringAfterLast('/')
}

@Suppress("TooGenericExceptionThrown")
private fun ensureSuccess(result: ProcessResult, action: String) {
    if (result.exitCode != 0) {
        val detail = result.stderr.ifBlank { result.stdout.ifBlank { "exit code ${result.exitCode}" } }
        throw RuntimeException("Failed to $action: ${detail.trim()}")
    }
}

@Suppress("UNCHECKED_CAST")
private fun appCacheTempDir(name: String): Path {
    val cacheDirs = NSSearchPathForDirectoriesInDomains(
        NSCachesDirectory,
        NSUserDomainMask,
        true
    ) as List<String>
    val cacheRoot = cacheDirs.firstOrNull()?.toPath()
        ?: FileSystem.SYSTEM_TEMPORARY_DIRECTORY
    return cacheRoot / "com.wire.kalium" / name
}
