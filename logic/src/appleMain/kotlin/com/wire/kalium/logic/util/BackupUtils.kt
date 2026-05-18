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
@file:Suppress("TooManyFunctions", "LongMethod", "NestedBlockDepth", "ComplexMethod", "ReturnCount")

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
import okio.BufferedSink
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
import platform.zlib.deflateEnd
import platform.zlib.deflateInit2
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2
import platform.zlib.z_stream

@Suppress("TooGenericExceptionCaught")
internal actual fun createCompressedFile(files: List<Pair<Source, String>>, outputSink: Sink): Either<CoreFailure, Long> = try {
    require(files.isNotEmpty()) { "Cannot create a compressed backup without files" }

    var bytesWritten = 0L
    outputSink.buffer().use { sink ->
        val centralDirectoryEntries = mutableListOf<ZipEntryMetadata>()

        files.forEach { (source, fileName) ->
            val sanitizedFileName = sanitizeFileName(fileName)
            val localHeaderOffset = bytesWritten
            bytesWritten += sink.writeStreamingLocalHeader(sanitizedFileName)
            val deflateResult = source.buffer().use { input -> streamDeflate(input, sink) }
            bytesWritten += deflateResult.compressedSize
            bytesWritten += sink.writeDataDescriptor(
                crc = deflateResult.crc,
                compressedSize = deflateResult.compressedSize,
                uncompressedSize = deflateResult.uncompressedSize,
            )
            centralDirectoryEntries += ZipEntryMetadata(
                name = sanitizedFileName,
                compressionMethod = COMPRESSION_METHOD_DEFLATE,
                crc = deflateResult.crc,
                compressedSize = deflateResult.compressedSize,
                uncompressedSize = deflateResult.uncompressedSize,
                localHeaderOffset = localHeaderOffset,
            )
        }

        val centralDirectoryOffset = bytesWritten
        var centralDirectorySize = 0L
        centralDirectoryEntries.forEach { entry ->
            centralDirectorySize += sink.writeCentralDirectoryHeader(entry)
        }
        bytesWritten += centralDirectorySize
        bytesWritten += sink.writeZip64EndOfCentralDirectoryIfNeeded(
            entryCount = centralDirectoryEntries.size,
            centralDirectorySize = centralDirectorySize,
            centralDirectoryOffset = centralDirectoryOffset,
        )
        bytesWritten += sink.writeEndOfCentralDirectory(
            entryCount = centralDirectoryEntries.size,
            centralDirectorySize = centralDirectorySize,
            centralDirectoryOffset = centralDirectoryOffset,
        )
    }
    Either.Right(bytesWritten)
} catch (e: Exception) {
    Either.Left(StorageFailure.Generic(RuntimeException("There was an error trying to compress the provided files", e)))
}

@Suppress("TooGenericExceptionCaught")
internal actual fun extractCompressedFile(
    inputSource: Source,
    outputRootPath: Path,
    param: ExtractFilesParam,
    fileSystem: KaliumFileSystem
): Either<CoreFailure, Long> = try {
    fileSystem.createDirectories(outputRootPath)
    var totalExtractedFilesSize = 0L

    inputSource.buffer().use { source ->
        val reader = StreamingZipReader(source)
        while (true) {
            val signature = reader.readIntLe()
            if (signature == CENTRAL_DIRECTORY_HEADER_SIGNATURE) break
            require(signature == LOCAL_FILE_HEADER_SIGNATURE) { "Unexpected signature in ZIP stream" }
            val header = reader.readLocalFileHeader()
            require(!isInvalidEntryPathDestination(header.name)) { "Archive contains invalid entry path" }
            val shouldExtract = when (param) {
                ExtractFilesParam.All -> true
                is ExtractFilesParam.Only -> header.name in param.files
            }
            if (shouldExtract) {
                val outputPath = (outputRootPath / header.name).normalized()
                val extracted = fileSystem.sink(outputPath).buffer().use { entrySink ->
                    extractEntry(reader, entrySink, header)
                }
                totalExtractedFilesSize += extracted
            } else {
                extractEntry(reader, output = null, header = header)
            }
        }
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
): Either<CoreFailure, Map<String, Boolean>> = try {
    if (!fileSystem.exists(compressedFilePath)) {
        return Either.Left(StorageFailure.DataNotFound)
    }
    val foundExtensions = mutableSetOf<String>()
    fileSystem.source(compressedFilePath).buffer().use { source ->
        val reader = StreamingZipReader(source)
        while (true) {
            val signature = reader.readIntLe()
            if (signature != LOCAL_FILE_HEADER_SIGNATURE) break
            val header = reader.readLocalFileHeader()
            foundExtensions += header.name.substringAfterLast('.', "")
            extractEntry(reader, output = null, header = header)
        }
    }
    Either.Right(expectedFileExtensions.associateWith { it in foundExtensions })
} catch (e: Exception) {
    Either.Left(StorageFailure.Generic(RuntimeException("There was an error trying to validate the provided compressed file", e)))
}

internal actual inline fun <reified T> decodeBufferSequence(bufferedSource: BufferedSource): Sequence<T> {
    val jsonString = bufferedSource.readUtf8()
    return KtxWebSerializer.json.decodeFromString<List<T>>(jsonString).asSequence()
}

private data class ZipEntryMetadata(
    val name: String,
    val compressionMethod: Int,
    val crc: UInt,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val localHeaderOffset: Long,
)

private data class LocalFileHeader(
    val generalPurposeFlag: Int,
    val compressionMethod: Int,
    val crc: UInt,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val name: String,
) {
    val hasDataDescriptor: Boolean
        get() = (generalPurposeFlag and GENERAL_PURPOSE_DATA_DESCRIPTOR_FLAG) != 0
}

private data class DeflateResult(
    val crc: UInt,
    val compressedSize: Long,
    val uncompressedSize: Long,
)

private fun BufferedSink.writeStreamingLocalHeader(name: String): Long {
    val fileName = name.encodeToByteArray()
    writeIntLe(LOCAL_FILE_HEADER_SIGNATURE)
    writeShortLe(ZIP_VERSION_NEEDED)
    writeShortLe(GENERAL_PURPOSE_UTF8_FLAG or GENERAL_PURPOSE_DATA_DESCRIPTOR_FLAG)
    writeShortLe(COMPRESSION_METHOD_DEFLATE)
    writeShortLe(0)
    writeShortLe(0)
    writeIntLe(0)
    writeIntLe(0)
    writeIntLe(0)
    writeShortLe(fileName.size)
    writeShortLe(0)
    write(fileName)
    return (LOCAL_FILE_HEADER_FIXED_SIZE + fileName.size).toLong()
}

private fun BufferedSink.writeDataDescriptor(crc: UInt, compressedSize: Long, uncompressedSize: Long): Long {
    writeIntLe(DATA_DESCRIPTOR_SIGNATURE)
    writeIntLe(crc.toInt())
    return if (compressedSize.requiresZip64() || uncompressedSize.requiresZip64()) {
        writeLongLe(compressedSize)
        writeLongLe(uncompressedSize)
        ZIP64_DATA_DESCRIPTOR_SIZE.toLong()
    } else {
        writeIntLe(compressedSize.toInt())
        writeIntLe(uncompressedSize.toInt())
        DATA_DESCRIPTOR_SIZE.toLong()
    }
}

private fun BufferedSink.writeCentralDirectoryHeader(entry: ZipEntryMetadata): Long {
    val fileName = entry.name.encodeToByteArray()
    val zip64Extra = entry.zip64CentralDirectoryExtraField()
    writeIntLe(CENTRAL_DIRECTORY_HEADER_SIGNATURE)
    writeShortLe(if (zip64Extra.isEmpty()) ZIP_VERSION_MADE_BY else ZIP64_VERSION_MADE_BY)
    writeShortLe(if (zip64Extra.isEmpty()) ZIP_VERSION_NEEDED else ZIP64_VERSION_NEEDED)
    writeShortLe(GENERAL_PURPOSE_UTF8_FLAG or GENERAL_PURPOSE_DATA_DESCRIPTOR_FLAG)
    writeShortLe(entry.compressionMethod)
    writeShortLe(0)
    writeShortLe(0)
    writeIntLe(entry.crc.toInt())
    writeIntLe(entry.compressedSize.toZip32Field())
    writeIntLe(entry.uncompressedSize.toZip32Field())
    writeShortLe(fileName.size)
    writeShortLe(zip64Extra.size)
    writeShortLe(0)
    writeShortLe(0)
    writeShortLe(0)
    writeIntLe(0)
    writeIntLe(entry.localHeaderOffset.toZip32Field())
    write(fileName)
    write(zip64Extra)
    return (CENTRAL_DIRECTORY_FIXED_SIZE + fileName.size + zip64Extra.size).toLong()
}

private fun ZipEntryMetadata.zip64CentralDirectoryExtraField(): ByteArray {
    val needsUncompressedSize = uncompressedSize.requiresZip64()
    val needsCompressedSize = compressedSize.requiresZip64()
    val needsLocalHeaderOffset = localHeaderOffset.requiresZip64()
    if (!needsUncompressedSize && !needsCompressedSize && !needsLocalHeaderOffset) return ByteArray(0)

    val extra = Buffer()
    if (needsUncompressedSize) extra.writeLongLe(uncompressedSize)
    if (needsCompressedSize) extra.writeLongLe(compressedSize)
    if (needsLocalHeaderOffset) extra.writeLongLe(localHeaderOffset)

    val payload = extra.readByteArray()
    return Buffer()
        .writeShortLe(ZIP64_EXTRA_FIELD_ID)
        .writeShortLe(payload.size)
        .write(payload)
        .readByteArray()
}

private fun BufferedSink.writeEndOfCentralDirectory(
    entryCount: Int,
    centralDirectorySize: Long,
    centralDirectoryOffset: Long,
): Long {
    writeIntLe(END_OF_CENTRAL_DIRECTORY_SIGNATURE)
    writeShortLe(0)
    writeShortLe(0)
    writeShortLe(entryCount.toZip16Field())
    writeShortLe(entryCount.toZip16Field())
    writeIntLe(centralDirectorySize.toZip32Field())
    writeIntLe(centralDirectoryOffset.toZip32Field())
    writeShortLe(0)
    return EOCD_FIXED_SIZE.toLong()
}

private fun BufferedSink.writeZip64EndOfCentralDirectoryIfNeeded(
    entryCount: Int,
    centralDirectorySize: Long,
    centralDirectoryOffset: Long,
): Long {
    val needsZip64 = entryCount > ZIP16_MAX_VALUE ||
            centralDirectorySize.requiresZip64() ||
            centralDirectoryOffset.requiresZip64()
    if (!needsZip64) return 0L

    val zip64EndOfCentralDirectoryOffset = centralDirectoryOffset + centralDirectorySize

    writeIntLe(ZIP64_END_OF_CENTRAL_DIRECTORY_SIGNATURE)
    writeLongLe(ZIP64_EOCD_RECORD_SIZE)
    writeShortLe(ZIP64_VERSION_MADE_BY)
    writeShortLe(ZIP64_VERSION_NEEDED)
    writeIntLe(0)
    writeIntLe(0)
    writeLongLe(entryCount.toLong())
    writeLongLe(entryCount.toLong())
    writeLongLe(centralDirectorySize)
    writeLongLe(centralDirectoryOffset)

    writeIntLe(ZIP64_END_OF_CENTRAL_DIRECTORY_LOCATOR_SIGNATURE)
    writeIntLe(0)
    writeLongLe(zip64EndOfCentralDirectoryOffset)
    writeIntLe(1)

    return ZIP64_EOCD_TOTAL_SIZE.toLong()
}

private class StreamingZipReader(private val source: BufferedSource) {
    private val head = Buffer()

    private fun ensureAvailable(byteCount: Int) {
        while (head.size < byteCount.toLong()) {
            val needed = byteCount.toLong() - head.size
            val read = source.read(head, needed)
            if (read == -1L) throw IllegalStateException("Unexpected end of ZIP stream")
        }
    }

    fun readIntLe(): Int {
        ensureAvailable(Int.SIZE_BYTES)
        return head.readIntLe()
    }

    fun readShortLe(): Int {
        ensureAvailable(Short.SIZE_BYTES)
        return head.readShortLe().toInt() and SHORT_MASK
    }

    fun readByteArray(byteCount: Int): ByteArray {
        ensureAvailable(byteCount)
        return head.readByteArray(byteCount.toLong())
    }

    fun skip(byteCount: Int) {
        ensureAvailable(byteCount)
        head.skip(byteCount.toLong())
    }

    fun fillInto(out: ByteArray, offset: Int, length: Int): Int {
        if (head.size > 0L) {
            return head.read(out, offset, minOf(length.toLong(), head.size).toInt())
        }
        return source.read(out, offset, length)
    }

    fun unread(data: ByteArray, offset: Int, length: Int) {
        if (length <= 0) return
        if (head.size == 0L) {
            head.write(data, offset, length)
            return
        }
        val existing = head.readByteArray()
        head.write(data, offset, length)
        head.write(existing)
    }

    fun unreadIntLe(value: Int) {
        unread(
            byteArrayOf(
                value.toByte(),
                (value shr BITS_PER_BYTE).toByte(),
                (value shr (BITS_PER_BYTE * BYTE_2)).toByte(),
                (value shr (BITS_PER_BYTE * BYTE_3)).toByte(),
            ),
            0,
            Int.SIZE_BYTES,
        )
    }
}

private fun StreamingZipReader.readLocalFileHeader(): LocalFileHeader {
    readShortLe()
    val generalPurposeFlag = readShortLe()
    val compressionMethod = readShortLe()
    readShortLe()
    readShortLe()
    val crc = readIntLe().toUInt()
    val compressedSize = readIntLe().toUnsignedLong()
    val uncompressedSize = readIntLe().toUnsignedLong()
    val fileNameLength = readShortLe()
    val extraFieldLength = readShortLe()
    val nameBytes = readByteArray(fileNameLength)
    val extraFields = if (extraFieldLength > 0) readByteArray(extraFieldLength) else ByteArray(0)
    val zip64Extra = extraFields.readZip64ExtraField(
        compressedSize = compressedSize,
        uncompressedSize = uncompressedSize,
    )
    return LocalFileHeader(
        generalPurposeFlag = generalPurposeFlag,
        compressionMethod = compressionMethod,
        crc = crc,
        compressedSize = zip64Extra?.compressedSize ?: compressedSize,
        uncompressedSize = zip64Extra?.uncompressedSize ?: uncompressedSize,
        name = nameBytes.decodeToString(),
    )
}

private fun extractEntry(reader: StreamingZipReader, output: BufferedSink?, header: LocalFileHeader): Long =
    when (header.compressionMethod) {
        COMPRESSION_METHOD_STORED -> {
            require(!header.hasDataDescriptor) { "STORED compression with data descriptor is not supported" }
            copyStored(reader, output, header)
        }
        COMPRESSION_METHOD_DEFLATE -> inflateEntry(reader, output, header)
        else -> throw IllegalArgumentException("Unsupported ZIP compression method: ${header.compressionMethod}")
    }

private fun copyStored(reader: StreamingZipReader, output: BufferedSink?, header: LocalFileHeader): Long {
    val buf = ByteArray(STREAMING_BUFFER_SIZE)
    var remaining = header.compressedSize
    var crc = 0u
    while (remaining > 0) {
        val toRead = minOf(remaining, buf.size.toLong()).toInt()
        val n = reader.fillInto(buf, 0, toRead)
        if (n <= 0) throw IllegalStateException("Unexpected end of stream while reading STORED entry")
        crc = crc32Update(crc, buf, 0, n)
        output?.write(buf, 0, n)
        remaining -= n
    }
    require(crc == header.crc) { "Invalid CRC for ZIP entry ${header.name}" }
    return header.uncompressedSize
}

private fun inflateEntry(reader: StreamingZipReader, output: BufferedSink?, header: LocalFileHeader): Long = memScoped {
    val stream = alloc<z_stream>()
    memset(stream.ptr, 0, sizeOf<z_stream>().convert())
    val initResult = inflateInit2(stream.ptr, -MAX_WBITS)
    check(initResult == Z_OK) { "Failed to initialize zlib inflate: $initResult" }

    val inputBuffer = ByteArray(STREAMING_BUFFER_SIZE)
    val outputBuffer = ByteArray(STREAMING_BUFFER_SIZE)
    var crc = 0u
    var uncompressedSize = 0L

    try {
        outputBuffer.usePinned { outputPinned ->
            val outPtr: CPointer<UByteVar> = outputPinned.addressOf(0).reinterpret()
            var done = false
            while (!done) {
                val inputLen = reader.fillInto(inputBuffer, 0, inputBuffer.size)
                if (inputLen <= 0) throw IllegalStateException("Unexpected end of compressed stream for ${header.name}")

                inputBuffer.usePinned { inputPinned ->
                    stream.next_in = inputPinned.addressOf(0).reinterpret()
                    stream.avail_in = inputLen.convert()

                    while (stream.avail_in.toInt() > 0 && !done) {
                        stream.next_out = outPtr
                        stream.avail_out = outputBuffer.size.convert()

                        val result = inflate(stream.ptr, Z_NO_FLUSH)
                        check(result == Z_OK || result == Z_STREAM_END) {
                            "Failed to inflate ZIP entry ${header.name}: $result"
                        }

                        val produced = outputBuffer.size - stream.avail_out.toInt()
                        if (produced > 0) {
                            uncompressedSize += produced
                            crc = crc32Update(crc, outputBuffer, 0, produced)
                            output?.write(outputBuffer, 0, produced)
                        }
                        if (result == Z_STREAM_END) done = true
                    }
                    val unused = stream.avail_in.toInt()
                    if (done && unused > 0) {
                        reader.unread(inputBuffer, inputLen - unused, unused)
                    }
                }
            }
        }

        if (header.hasDataDescriptor) {
            val descriptor = reader.readDataDescriptor()
            require(crc == descriptor.crc) { "Invalid CRC for ZIP entry ${header.name}" }
            require(uncompressedSize == descriptor.uncompressedSize) { "Invalid size for ZIP entry ${header.name}" }
        } else {
            require(crc == header.crc) { "Invalid CRC for ZIP entry ${header.name}" }
            require(uncompressedSize == header.uncompressedSize) { "Invalid size for ZIP entry ${header.name}" }
        }

        uncompressedSize
    } finally {
        inflateEnd(stream.ptr)
    }
}

private fun streamDeflate(input: BufferedSource, output: BufferedSink): DeflateResult = memScoped {
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

    val inputBuffer = ByteArray(STREAMING_BUFFER_SIZE)
    val outputBuffer = ByteArray(STREAMING_BUFFER_SIZE)
    var crc = 0u
    var compressedSize = 0L
    var uncompressedSize = 0L

    try {
        outputBuffer.usePinned { outputPinned ->
            val outPtr: CPointer<UByteVar> = outputPinned.addressOf(0).reinterpret()

            var inputLen = input.read(inputBuffer, 0, inputBuffer.size)
            while (inputLen != -1) {
                if (inputLen > 0) {
                    uncompressedSize += inputLen
                    crc = crc32Update(crc, inputBuffer, 0, inputLen)

                    inputBuffer.usePinned { inputPinned ->
                        stream.next_in = inputPinned.addressOf(0).reinterpret()
                        stream.avail_in = inputLen.convert()

                        while (stream.avail_in.toInt() > 0) {
                            stream.next_out = outPtr
                            stream.avail_out = outputBuffer.size.convert()

                            val result = deflate(stream.ptr, Z_NO_FLUSH)
                            check(result == Z_OK) { "Failed to deflate data: $result" }

                            val produced = outputBuffer.size - stream.avail_out.toInt()
                            if (produced > 0) {
                                output.write(outputBuffer, 0, produced)
                                compressedSize += produced
                            }
                        }
                    }
                }
                inputLen = input.read(inputBuffer, 0, inputBuffer.size)
            }

            var finishResult = Z_OK
            while (finishResult != Z_STREAM_END) {
                stream.next_in = null
                stream.avail_in = 0u.convert()
                stream.next_out = outPtr
                stream.avail_out = outputBuffer.size.convert()

                finishResult = deflate(stream.ptr, Z_FINISH)
                check(finishResult == Z_OK || finishResult == Z_STREAM_END) {
                    "Failed to finish deflate: $finishResult"
                }

                val produced = outputBuffer.size - stream.avail_out.toInt()
                if (produced > 0) {
                    output.write(outputBuffer, 0, produced)
                    compressedSize += produced
                }
            }
        }
        DeflateResult(crc, compressedSize, uncompressedSize)
    } finally {
        deflateEnd(stream.ptr)
    }
}

private fun crc32Update(prev: UInt, data: ByteArray, offset: Int, length: Int): UInt {
    if (length == 0) return prev
    return data.usePinned { pinned ->
        val ptr: CPointer<UByteVar> = pinned.addressOf(offset).reinterpret()
        crc32(prev.convert(), ptr, length.convert()).toUInt()
    }
}

private data class Zip64ExtraField(
    val compressedSize: Long?,
    val uncompressedSize: Long?,
)

private data class DataDescriptor(
    val crc: UInt,
    val compressedSize: Long,
    val uncompressedSize: Long,
)

private fun StreamingZipReader.readDataDescriptor(): DataDescriptor {
    val first = readIntLe()
    val descriptorCrc: UInt = if (first == DATA_DESCRIPTOR_SIGNATURE) {
        readIntLe().toUInt()
    } else {
        first.toUInt()
    }

    val compressedSize32 = readIntLe()
    val secondSizeOrCompressedHigh = readIntLe()
    val next = readIntLe()
    return if (next.isZipHeaderSignature()) {
        unreadIntLe(next)
        DataDescriptor(
            crc = descriptorCrc,
            compressedSize = compressedSize32.toUnsignedLong(),
            uncompressedSize = secondSizeOrCompressedHigh.toUnsignedLong(),
        )
    } else {
        val uncompressedHigh = readIntLe()
        DataDescriptor(
            crc = descriptorCrc,
            compressedSize = compressedSize32.toUnsignedLong() or (secondSizeOrCompressedHigh.toUnsignedLong() shl INT_BITS),
            uncompressedSize = next.toUnsignedLong() or (uncompressedHigh.toUnsignedLong() shl INT_BITS),
        )
    }
}

private fun ByteArray.readZip64ExtraField(compressedSize: Long, uncompressedSize: Long): Zip64ExtraField? {
    var offset = 0
    while (offset + ZIP_EXTRA_FIELD_HEADER_SIZE <= size) {
        val headerId = readUInt16Le(offset)
        val dataSize = readUInt16Le(offset + Short.SIZE_BYTES)
        offset += ZIP_EXTRA_FIELD_HEADER_SIZE
        if (offset + dataSize > size) return null
        if (headerId == ZIP64_EXTRA_FIELD_ID) {
            var zip64Offset = offset
            val zip64UncompressedSize = if (uncompressedSize == ZIP32_MAX_VALUE) {
                readInt64Le(zip64Offset).also { zip64Offset += Long.SIZE_BYTES }
            } else {
                null
            }
            val zip64CompressedSize = if (compressedSize == ZIP32_MAX_VALUE) {
                readInt64Le(zip64Offset)
            } else {
                null
            }
            return Zip64ExtraField(
                compressedSize = zip64CompressedSize,
                uncompressedSize = zip64UncompressedSize,
            )
        }
        offset += dataSize
    }
    return null
}

private fun Long.requiresZip64(): Boolean = this > ZIP32_MAX_VALUE

private fun Long.toZip32Field(): Int =
    if (requiresZip64()) ZIP32_MAX_VALUE.toInt() else toInt()

private fun Int.toZip16Field(): Int =
    if (this > ZIP16_MAX_VALUE) ZIP16_MAX_VALUE else this

private fun Int.toUnsignedLong(): Long = toUInt().toLong()

private fun Int.isZipHeaderSignature(): Boolean =
    this == LOCAL_FILE_HEADER_SIGNATURE ||
            this == CENTRAL_DIRECTORY_HEADER_SIGNATURE ||
            this == END_OF_CENTRAL_DIRECTORY_SIGNATURE ||
            this == ZIP64_END_OF_CENTRAL_DIRECTORY_SIGNATURE ||
            this == ZIP64_END_OF_CENTRAL_DIRECTORY_LOCATOR_SIGNATURE

private fun ByteArray.readUInt16Le(offset: Int): Int =
    (this[offset].toInt() and BYTE_MASK) or
            ((this[offset + BYTE_1].toInt() and BYTE_MASK) shl BITS_PER_BYTE)

private fun ByteArray.readInt64Le(offset: Int): Long {
    require(offset + Long.SIZE_BYTES <= size) { "Invalid ZIP64 extra field" }
    var result = 0L
    repeat(Long.SIZE_BYTES) { index ->
        result = result or ((this[offset + index].toLong() and BYTE_MASK.toLong()) shl (BITS_PER_BYTE * index))
    }
    require(result >= 0) { "ZIP64 value exceeds supported signed 64-bit range" }
    return result
}

private fun sanitizeFileName(fileName: String): String {
    require(fileName.isNotBlank()) { "File name cannot be blank" }
    require(!isInvalidEntryPathDestination(fileName)) { "Invalid file name: $fileName" }
    return fileName.substringAfterLast('/')
}

private const val LOCAL_FILE_HEADER_SIGNATURE = 0x04034b50
private const val CENTRAL_DIRECTORY_HEADER_SIGNATURE = 0x02014b50
private const val END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054b50
private const val DATA_DESCRIPTOR_SIGNATURE = 0x08074b50
private const val ZIP64_END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06064b50
private const val ZIP64_END_OF_CENTRAL_DIRECTORY_LOCATOR_SIGNATURE = 0x07064b50

private const val ZIP_VERSION_MADE_BY = 0x0314
private const val ZIP_VERSION_NEEDED = 20
private const val ZIP64_VERSION_MADE_BY = 0x032d
private const val ZIP64_VERSION_NEEDED = 45
private const val GENERAL_PURPOSE_UTF8_FLAG = 0x0800
private const val GENERAL_PURPOSE_DATA_DESCRIPTOR_FLAG = 0x0008
private const val COMPRESSION_METHOD_STORED = 0
private const val COMPRESSION_METHOD_DEFLATE = 8
private const val ZLIB_MEMORY_LEVEL = 8
private const val BYTE_MASK = 0xff
private const val BITS_PER_BYTE = 8
private const val BYTE_1 = 1
private const val BYTE_2 = 2
private const val BYTE_3 = 3
private const val INT_BITS = 32
private const val SHORT_MASK = 0xFFFF
private const val STREAMING_BUFFER_SIZE = 8192
private const val ZIP32_MAX_VALUE = 0xFFFF_FFFFL
private const val ZIP16_MAX_VALUE = 0xFFFF
private const val ZIP64_EXTRA_FIELD_ID = 0x0001
private const val ZIP_EXTRA_FIELD_HEADER_SIZE = 4

private const val LOCAL_FILE_HEADER_FIXED_SIZE = 30
private const val CENTRAL_DIRECTORY_FIXED_SIZE = 46
private const val EOCD_FIXED_SIZE = 22
private const val DATA_DESCRIPTOR_SIZE = 16
private const val ZIP64_DATA_DESCRIPTOR_SIZE = 24
private const val ZIP64_EOCD_RECORD_SIZE = 44L
private const val ZIP64_EOCD_TOTAL_SIZE = 76
