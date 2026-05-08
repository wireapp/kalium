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
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
@file:Suppress("TooManyFunctions")

package com.wire.kalium.logic.util

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.data.web.KtxWebSerializer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import okio.Buffer
import okio.BufferedSource
import okio.Path
import okio.Sink
import okio.Source
import okio.buffer
import okio.use
import platform.posix.memset
import platform.zlib.MAX_WBITS
import platform.zlib.Z_BEST_SPEED
import platform.zlib.Z_DEFAULT_STRATEGY
import platform.zlib.Z_DEFLATED
import platform.zlib.Z_FINISH
import platform.zlib.Z_NO_FLUSH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.crc32
import platform.zlib.deflate
import platform.zlib.deflateBound
import platform.zlib.deflateEnd
import platform.zlib.deflateInit2
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2
import platform.zlib.z_stream

@Suppress("TooGenericExceptionCaught")
internal actual fun createCompressedFile(files: List<Pair<Source, String>>, outputSink: Sink): Either<CoreFailure, Long> = try {
    require(files.isNotEmpty()) { "Cannot create a compressed backup without files" }

    val entries = files.map { (source, fileName) ->
        val sanitizedFileName = sanitizeFileName(fileName)
        val content = source.buffer().use { it.readByteArray() }
        val compressedContent = deflateRaw(content)
        ZipEntryData(
            name = sanitizedFileName,
            content = content,
            compressedContent = compressedContent,
            crc = crc32(content),
        )
    }

    val zipData = buildZip(entries)
    outputSink.buffer().use { sink ->
        sink.write(zipData)
    }
    Either.Right(zipData.size.toLong())
} catch (e: Exception) {
    Either.Left(StorageFailure.Generic(RuntimeException("There was an error trying to compress the provided files", e)))
}

@Suppress("TooGenericExceptionCaught", "NestedBlockDepth")
internal actual fun extractCompressedFile(
    inputSource: Source,
    outputRootPath: Path,
    param: ExtractFilesParam,
    fileSystem: KaliumFileSystem
): Either<CoreFailure, Long> = try {
    val archive = ZipArchive(inputSource.buffer().use { it.readByteArray() })
    val entries = archive.entries()
    val targetEntries = when (param) {
        ExtractFilesParam.All -> entries
        is ExtractFilesParam.Only -> entries.filter { it.name in param.files }
    }

    if (targetEntries.any { isInvalidEntryPathDestination(it.name) }) {
        throw IllegalArgumentException("Archive contains invalid entry path")
    }

    fileSystem.createDirectories(outputRootPath)
    var totalExtractedFilesSize = 0L
    targetEntries.forEach { entry ->
        val outputPath = (outputRootPath / entry.name).normalized()
        val data = archive.readEntry(entry)
        fileSystem.sink(outputPath).buffer().use { sink ->
            sink.write(data)
        }
        totalExtractedFilesSize += data.size
    }

    Either.Right(totalExtractedFilesSize)
} catch (e: Exception) {
    kaliumLogger.e("Error extracting compressed backup file", e)
    Either.Left(StorageFailure.Generic(RuntimeException("There was an error trying to extract the provided compressed file", e)))
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

        val archive = ZipArchive(fileSystem.source(compressedFilePath).buffer().use { it.readByteArray() })
        val foundExtensions = archive.entries().map { it.name.substringAfterLast('.', "") }.toSet()
        Either.Right(expectedFileExtensions.associateWith { it in foundExtensions })
    } catch (e: Exception) {
        Either.Left(StorageFailure.Generic(RuntimeException("There was an error trying to validate the provided compressed file", e)))
    }

internal actual inline fun <reified T> decodeBufferSequence(bufferedSource: BufferedSource): Sequence<T> {
    val jsonString = bufferedSource.readUtf8()
    return KtxWebSerializer.json.decodeFromString<List<T>>(jsonString).asSequence()
}

private data class ZipEntryData(
    val name: String,
    val content: ByteArray,
    val compressedContent: ByteArray,
    val crc: UInt,
)

private data class ZipEntryMetadata(
    val name: String,
    val compressionMethod: Int,
    val crc: UInt,
    val compressedSize: Int,
    val uncompressedSize: Int,
    val localHeaderOffset: Int,
)

private fun buildZip(entries: List<ZipEntryData>): ByteArray {
    val output = Buffer()
    val centralDirectory = Buffer()
    val centralDirectoryEntries = mutableListOf<Pair<ZipEntryData, Int>>()

    entries.forEach { entry ->
        val localHeaderOffset = output.size.toInt()
        centralDirectoryEntries += entry to localHeaderOffset
        output.writeLocalHeader(entry)
        output.write(entry.compressedContent)
    }

    val centralDirectoryOffset = output.size.toInt()
    centralDirectoryEntries.forEach { (entry, localHeaderOffset) ->
        centralDirectory.writeCentralDirectoryHeader(entry, localHeaderOffset)
    }
    val centralDirectorySize = centralDirectory.size.toInt()
    output.writeAll(centralDirectory)
    output.writeEndOfCentralDirectory(entries.size, centralDirectorySize, centralDirectoryOffset)

    return output.readByteArray()
}

private fun Buffer.writeLocalHeader(entry: ZipEntryData) {
    val fileName = entry.name.encodeToByteArray()
    writeIntLe(LOCAL_FILE_HEADER_SIGNATURE)
    writeShortLe(ZIP_VERSION_NEEDED)
    writeShortLe(GENERAL_PURPOSE_UTF8_FLAG)
    writeShortLe(COMPRESSION_METHOD_DEFLATE)
    writeShortLe(0)
    writeShortLe(0)
    writeIntLe(entry.crc.toInt())
    writeIntLe(entry.compressedContent.size)
    writeIntLe(entry.content.size)
    writeShortLe(fileName.size)
    writeShortLe(0)
    write(fileName)
}

private fun Buffer.writeCentralDirectoryHeader(entry: ZipEntryData, localHeaderOffset: Int) {
    val fileName = entry.name.encodeToByteArray()
    writeIntLe(CENTRAL_DIRECTORY_HEADER_SIGNATURE)
    writeShortLe(ZIP_VERSION_MADE_BY)
    writeShortLe(ZIP_VERSION_NEEDED)
    writeShortLe(GENERAL_PURPOSE_UTF8_FLAG)
    writeShortLe(COMPRESSION_METHOD_DEFLATE)
    writeShortLe(0)
    writeShortLe(0)
    writeIntLe(entry.crc.toInt())
    writeIntLe(entry.compressedContent.size)
    writeIntLe(entry.content.size)
    writeShortLe(fileName.size)
    writeShortLe(0)
    writeShortLe(0)
    writeShortLe(0)
    writeShortLe(0)
    writeIntLe(0)
    writeIntLe(localHeaderOffset)
    write(fileName)
}

private fun Buffer.writeEndOfCentralDirectory(entryCount: Int, centralDirectorySize: Int, centralDirectoryOffset: Int) {
    writeIntLe(END_OF_CENTRAL_DIRECTORY_SIGNATURE)
    writeShortLe(0)
    writeShortLe(0)
    writeShortLe(entryCount)
    writeShortLe(entryCount)
    writeIntLe(centralDirectorySize)
    writeIntLe(centralDirectoryOffset)
    writeShortLe(0)
}

private class ZipArchive(private val data: ByteArray) {

    fun entries(): List<ZipEntryMetadata> {
        val endOfCentralDirectoryOffset = findEndOfCentralDirectory()
        val entryCount = data.readUInt16Le(endOfCentralDirectoryOffset + EOCD_ENTRY_COUNT_OFFSET)
        val centralDirectoryOffset = data.readInt32Le(endOfCentralDirectoryOffset + EOCD_CENTRAL_DIRECTORY_OFFSET)

        val entries = mutableListOf<ZipEntryMetadata>()
        var offset = centralDirectoryOffset
        repeat(entryCount) {
            require(data.readInt32Le(offset) == CENTRAL_DIRECTORY_HEADER_SIGNATURE) { "Invalid central directory header" }
            val compressionMethod = data.readUInt16Le(offset + CD_COMPRESSION_METHOD_OFFSET)
            val crc = data.readUInt32Le(offset + CD_CRC_OFFSET)
            val compressedSize = data.readInt32Le(offset + CD_COMPRESSED_SIZE_OFFSET)
            val uncompressedSize = data.readInt32Le(offset + CD_UNCOMPRESSED_SIZE_OFFSET)
            val fileNameLength = data.readUInt16Le(offset + CD_FILE_NAME_LENGTH_OFFSET)
            val extraFieldLength = data.readUInt16Le(offset + CD_EXTRA_FIELD_LENGTH_OFFSET)
            val fileCommentLength = data.readUInt16Le(offset + CD_FILE_COMMENT_LENGTH_OFFSET)
            val localHeaderOffset = data.readInt32Le(offset + CD_LOCAL_HEADER_OFFSET)
            val fileNameOffset = offset + CENTRAL_DIRECTORY_FIXED_SIZE
            val name = data.decodeToString(fileNameOffset, fileNameOffset + fileNameLength)

            entries += ZipEntryMetadata(
                name = name,
                compressionMethod = compressionMethod,
                crc = crc,
                compressedSize = compressedSize,
                uncompressedSize = uncompressedSize,
                localHeaderOffset = localHeaderOffset,
            )

            offset = fileNameOffset + fileNameLength + extraFieldLength + fileCommentLength
        }
        return entries
    }

    fun readEntry(entry: ZipEntryMetadata): ByteArray {
        val offset = entry.localHeaderOffset
        require(data.readInt32Le(offset) == LOCAL_FILE_HEADER_SIGNATURE) { "Invalid local file header" }
        val fileNameLength = data.readUInt16Le(offset + LFH_FILE_NAME_LENGTH_OFFSET)
        val extraFieldLength = data.readUInt16Le(offset + LFH_EXTRA_FIELD_LENGTH_OFFSET)
        val compressedDataOffset = offset + LOCAL_FILE_HEADER_FIXED_SIZE + fileNameLength + extraFieldLength
        val compressedData = data.copyOfRange(compressedDataOffset, compressedDataOffset + entry.compressedSize)
        val uncompressedData = when (entry.compressionMethod) {
            COMPRESSION_METHOD_STORED -> compressedData
            COMPRESSION_METHOD_DEFLATE -> inflateRaw(compressedData, entry.uncompressedSize)
            else -> throw IllegalArgumentException("Unsupported ZIP compression method: ${entry.compressionMethod}")
        }
        require(crc32(uncompressedData) == entry.crc) { "Invalid CRC for ZIP entry ${entry.name}" }
        return uncompressedData
    }

    private fun findEndOfCentralDirectory(): Int {
        val minOffset = (data.size - MAX_EOCD_SEARCH).coerceAtLeast(0)
        for (offset in data.size - EOCD_FIXED_SIZE downTo minOffset) {
            if (data.readInt32Le(offset) == END_OF_CENTRAL_DIRECTORY_SIGNATURE) {
                return offset
            }
        }
        throw IllegalArgumentException("ZIP end of central directory not found")
    }
}

private fun sanitizeFileName(fileName: String): String {
    require(fileName.isNotBlank()) { "File name cannot be blank" }
    require(!isInvalidEntryPathDestination(fileName)) { "Invalid file name: $fileName" }
    return fileName.substringAfterLast('/')
}

private fun deflateRaw(input: ByteArray): ByteArray = memScoped {
    val stream = alloc<z_stream>()
    memset(stream.ptr, 0, sizeOf<z_stream>().convert())
    val initResult = deflateInit2(
        stream.ptr,
        Z_BEST_SPEED,
        Z_DEFLATED,
        -MAX_WBITS,
        ZLIB_MEMORY_LEVEL,
        Z_DEFAULT_STRATEGY,
    )
    check(initResult == Z_OK) { "Failed to initialize zlib deflate: $initResult" }

    try {
        val outputSize = deflateBound(stream.ptr, input.size.convert()).toInt().coerceAtLeast(MIN_ZLIB_OUTPUT_BUFFER_SIZE)
        val output = ByteArray(outputSize)
        input.usePinned { inputPinned ->
            output.usePinned { outputPinned ->
                stream.next_in = if (input.isEmpty()) null else inputPinned.addressOf(0).reinterpret()
                stream.avail_in = input.size.convert()
                stream.next_out = outputPinned.addressOf(0).reinterpret()
                stream.avail_out = output.size.convert()

                val result = deflate(stream.ptr, Z_FINISH)
                check(result == Z_STREAM_END) { "Failed to deflate data: $result" }
                output.copyOf(stream.total_out.toInt())
            }
        }
    } finally {
        deflateEnd(stream.ptr)
    }
}

private fun inflateRaw(input: ByteArray, expectedSize: Int): ByteArray = memScoped {
    val stream = alloc<z_stream>()
    memset(stream.ptr, 0, sizeOf<z_stream>().convert())
    val initResult = inflateInit2(stream.ptr, -MAX_WBITS)
    check(initResult == Z_OK) { "Failed to initialize zlib inflate: $initResult" }

    try {
        val output = ByteArray(expectedSize)
        input.usePinned { inputPinned ->
            output.usePinned { outputPinned ->
                stream.next_in = if (input.isEmpty()) null else inputPinned.addressOf(0).reinterpret()
                stream.avail_in = input.size.convert()
                stream.next_out = if (output.isEmpty()) null else outputPinned.addressOf(0).reinterpret()
                stream.avail_out = output.size.convert()

                val result = inflate(stream.ptr, Z_NO_FLUSH)
                check(result == Z_STREAM_END) { "Failed to inflate data: $result" }
                output.copyOf(stream.total_out.toInt())
            }
        }
    } finally {
        inflateEnd(stream.ptr)
    }
}

private fun crc32(input: ByteArray): UInt =
    input.usePinned { pinned ->
        val dataPointer: CPointer<UByteVar>? = if (input.isEmpty()) null else pinned.addressOf(0).reinterpret()
        crc32(0u, dataPointer, input.size.convert()).toUInt()
    }

private fun ByteArray.readUInt16Le(offset: Int): Int =
    (this[offset].toInt() and BYTE_MASK) or
            ((this[offset + BYTE_1].toInt() and BYTE_MASK) shl BITS_PER_BYTE)

private fun ByteArray.readInt32Le(offset: Int): Int =
    (this[offset].toInt() and BYTE_MASK) or
            ((this[offset + BYTE_1].toInt() and BYTE_MASK) shl BITS_PER_BYTE) or
            ((this[offset + BYTE_2].toInt() and BYTE_MASK) shl (BITS_PER_BYTE * BYTE_2)) or
            ((this[offset + BYTE_3].toInt() and BYTE_MASK) shl (BITS_PER_BYTE * BYTE_3))

private fun ByteArray.readUInt32Le(offset: Int): UInt =
    readInt32Le(offset).toUInt()

private const val LOCAL_FILE_HEADER_SIGNATURE = 0x04034b50
private const val CENTRAL_DIRECTORY_HEADER_SIGNATURE = 0x02014b50
private const val END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054b50

private const val ZIP_VERSION_MADE_BY = 0x0314
private const val ZIP_VERSION_NEEDED = 20
private const val GENERAL_PURPOSE_UTF8_FLAG = 0x0800
private const val COMPRESSION_METHOD_STORED = 0
private const val COMPRESSION_METHOD_DEFLATE = 8
private const val ZLIB_MEMORY_LEVEL = 8
private const val MIN_ZLIB_OUTPUT_BUFFER_SIZE = 64
private const val BYTE_MASK = 0xff
private const val BITS_PER_BYTE = 8
private const val BYTE_1 = 1
private const val BYTE_2 = 2
private const val BYTE_3 = 3

private const val LOCAL_FILE_HEADER_FIXED_SIZE = 30
private const val LFH_FILE_NAME_LENGTH_OFFSET = 26
private const val LFH_EXTRA_FIELD_LENGTH_OFFSET = 28

private const val CENTRAL_DIRECTORY_FIXED_SIZE = 46
private const val CD_COMPRESSION_METHOD_OFFSET = 10
private const val CD_CRC_OFFSET = 16
private const val CD_COMPRESSED_SIZE_OFFSET = 20
private const val CD_UNCOMPRESSED_SIZE_OFFSET = 24
private const val CD_FILE_NAME_LENGTH_OFFSET = 28
private const val CD_EXTRA_FIELD_LENGTH_OFFSET = 30
private const val CD_FILE_COMMENT_LENGTH_OFFSET = 32
private const val CD_LOCAL_HEADER_OFFSET = 42

private const val EOCD_FIXED_SIZE = 22
private const val EOCD_ENTRY_COUNT_OFFSET = 10
private const val EOCD_CENTRAL_DIRECTORY_OFFSET = 16
private const val MAX_ZIP_COMMENT_SIZE = 65_535
private const val MAX_EOCD_SEARCH = EOCD_FIXED_SIZE + MAX_ZIP_COMMENT_SIZE
