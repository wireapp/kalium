package com.wire.kalium.logic.util

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.functional.Either
import okio.Buffer
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
actual fun extractCompressedFile(inputSource: Source, outputRootPath: Path, fileSystem: KaliumFileSystem): Either<CoreFailure, Long> = try {
    var totalExtractedFilesSize = 0L
    ZipInputStream(inputSource.buffer().inputStream()).use { zipInputStream ->
        var entry: ZipEntry? = zipInputStream.nextEntry
        while (entry != null) {
            readCompressedEntry(zipInputStream, outputRootPath, fileSystem, entry).let {
                totalExtractedFilesSize += it.first
                entry = it.second
            }
        }
    }
    Either.Right(totalExtractedFilesSize)
} catch (e: Exception) {
    Either.Left(StorageFailure.Generic(RuntimeException("There was an error trying to extract the provided compressed file", e)))
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

@Suppress("TooGenericExceptionThrown")
private fun readCompressedEntry(
    zipInputStream: ZipInputStream,
    outputRootPath: Path,
    fileSystem: KaliumFileSystem,
    entry: ZipEntry
): Pair<Long, ZipEntry?> {
    if (isInvalidEntryPathDestination(entry.name)) {
        throw RuntimeException("The provided zip file is invalid or has invalid data")
    }

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
    zipInputStream.closeEntry()
    return totalExtractedFilesSize to zipInputStream.nextEntry
}

/**
 * Verification that the entry path is valid and does not contain any invalid characters leading to write in undesired directories.
 */
private fun isInvalidEntryPathDestination(entryName: String) = entryName.contains("../")

private const val BUFFER_SIZE = 8192L
