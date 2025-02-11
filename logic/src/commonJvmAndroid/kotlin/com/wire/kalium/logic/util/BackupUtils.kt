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

package com.wire.kalium.logic.util

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.data.web.KtxWebSerializer
import com.wire.kalium.common.functional.Either
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.decodeToSequence
import okio.Buffer
import okio.BufferedSource
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import okio.Source
import okio.buffer
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Suppress("TooGenericExceptionCaught")
actual fun createCompressedFile(files: List<Pair<Source, String>>, outputSink: Sink): Either<CoreFailure, Long> = try {
    var compressedFileSize = 0L
    ZipOutputStream(outputSink.buffer().outputStream()).use { zipOutputStream ->
        files.forEach { (fileSource, fileName) ->
            compressedFileSize += addToCompressedFile(zipOutputStream, fileSource, fileName)
        }
    }
    Either.Right(compressedFileSize)
} catch (e: Exception) {
    Either.Left(StorageFailure.Generic(RuntimeException("There was an error trying to compress the provided files", e)))
}

private fun addToCompressedFile(zipOutputStream: ZipOutputStream, fileSource: Source, fileName: String): Long {
    var compressedFileSize = 0L
    var byteCount: Long
    val entry = ZipEntry(fileName)
    zipOutputStream.putNextEntry(entry)
    fileSource.buffer().use { input ->
        val readBuffer = Buffer()
        while (input.read(readBuffer, BUFFER_SIZE).also { byteCount = it } != -1L) {
            zipOutputStream.write(readBuffer.readByteArray())
            compressedFileSize += byteCount
        }
        zipOutputStream.write(input.readByteArray())
    }
    zipOutputStream.closeEntry()
    return compressedFileSize
}

@Suppress("TooGenericExceptionCaught", "NestedBlockDepth")
actual fun extractCompressedFile(
    inputSource: Source,
    outputRootPath: Path,
    param: ExtractFilesParam,
    fileSystem: KaliumFileSystem
): Either<CoreFailure, Long> = try {
    var totalExtractedFilesSize = 0L
    ZipInputStream(inputSource.buffer().inputStream()).use { zipInputStream ->
        var entry: ZipEntry? = zipInputStream.nextEntry
        while (entry != null) {
            totalExtractedFilesSize += when (param) {
                is ExtractFilesParam.All -> readCompressedEntry(zipInputStream, outputRootPath, fileSystem, entry)
                is ExtractFilesParam.Only -> readAndExtractIfMatch(zipInputStream, outputRootPath, fileSystem, entry, param.files)
            }
            zipInputStream.closeEntry()
            entry = zipInputStream.nextEntry
        }
    }
    Either.Right(totalExtractedFilesSize)
} catch (e: Exception) {
    Either.Left(StorageFailure.Generic(RuntimeException("There was an error trying to extract the provided compressed file", e)))
}

private fun readAndExtractIfMatch(
    zipInputStream: ZipInputStream,
    outputRootPath: Path,
    fileSystem: KaliumFileSystem,
    entry: ZipEntry,
    fileNames: Set<String>
): Long {
    return entry.name.let {
        if (fileNames.contains(it)) {
            readCompressedEntry(zipInputStream, outputRootPath, fileSystem, entry)
        } else {
            0L
        }
    }
}

@Suppress("TooGenericExceptionCaught", "NestedBlockDepth")
actual fun checkIfCompressedFileContainsFileTypes(
    compressedFilePath: Path,
    fileSystem: KaliumFileSystem,
    expectedFileExtensions: List<String>
): Either<CoreFailure, Map<String, Boolean>> =
    try {
        val resultMap = expectedFileExtensions.associateWith { false }.toMutableMap()
        val inputSource = fileSystem.source(compressedFilePath)
        ZipInputStream(inputSource.buffer().inputStream()).use { zipInputStream ->
            var entry: ZipEntry? = zipInputStream.nextEntry
            while (entry != null) {
                val entryExtension = entry.name.substringAfterLast('.', "")
                if (resultMap.containsKey(entryExtension))
                    resultMap[entryExtension] = true
                entry = zipInputStream.nextEntry
            }
        }
        Either.Right(resultMap)
    } catch (e: Exception) {
        Either.Left(StorageFailure.Generic(RuntimeException("There was an error trying to validate the provided compressed file", e)))
    }

@OptIn(ExperimentalSerializationApi::class)
actual inline fun <reified T> decodeBufferSequence(bufferedSource: BufferedSource): Sequence<T> {
    return KtxWebSerializer.json.decodeToSequence(
        bufferedSource.inputStream(),
        DecodeSequenceMode.ARRAY_WRAPPED
    )
}

@Suppress("TooGenericExceptionThrown")
private fun readCompressedEntry(
    zipInputStream: ZipInputStream,
    outputRootPath: Path,
    fileSystem: KaliumFileSystem,
    entry: ZipEntry
): Long {
    var totalExtractedFilesSize = 0L
    var byteCount: Int
    val entryPathName = "$outputRootPath/${entry.name}"
    val outputSink = fileSystem.sink(entryPathName.toPath().normalized())
    outputSink.buffer().use { output ->
        while (zipInputStream.read().also { byteCount = it } != -1) {
            output.writeByte(byteCount)
            totalExtractedFilesSize += byteCount
        }
        output.write(zipInputStream.readBytes())
    }
    return totalExtractedFilesSize
}

/**
 * Verification that the entry path is valid and does not contain any invalid characters leading to write in undesired directories.
 */
private fun isInvalidEntryPathDestination(entryName: String) = entryName.contains("../")

private const val BUFFER_SIZE = 8192L
