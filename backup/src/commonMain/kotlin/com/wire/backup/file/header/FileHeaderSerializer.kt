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
package com.wire.backup.file.header

import okio.Buffer
import okio.Source

/**
 * Reads and writes a [BackupHeader] to data streams.
 *
 * TODO: Update the URL with link to develop or whatever
 * See [the file specifications](https://github.com/wireapp/kalium/blob/d14e6cfd1bb9297234233efd24602d380ff3ecb3/backup/README.md)
 */
internal interface FileHeaderSerializer {
    fun headerToBytes(header: BackupHeader): Buffer
    fun parseHeader(source: Source): ParseResult
}

internal interface ParseResult {
    data class Success(val header: BackupHeader) : ParseResult
    sealed interface Failure : ParseResult {
        /**
         * The initial bytes do not correspond to the [DefaultFileHeaderSerializer.FORMAT_IDENTIFIER_MAGIC_NUMBER].
         */
        data class UnknownFormat(val readFormat: String) : Failure

        /**
         * The [version] found in the backup is not supported. Either too old, or too new.
         */
        data class UnsupportedVersion(val version: Int) : Failure
    }
}

internal class DefaultFileHeaderSerializer : FileHeaderSerializer {


    override fun headerToBytes(header: BackupHeader): Buffer {
        val headerBytes = Buffer()
        FileHeaderField.String.format.write(FORMAT_IDENTIFIER_MAGIC_NUMBER, headerBytes)
        repeat(SIZE_OF_GAP_AFTER_FORMAT_FIELD.toInt()) {
            headerBytes.writeByte(0x00)
        }
        FileHeaderField.UShort.version.write(header.version.toUShort(), headerBytes)
        FileHeaderField.UByteArray.salt.write(header.hashData.salt, headerBytes)
        FileHeaderField.UByteArray.hashedUserId.write(header.hashData.hashedUserId, headerBytes)
        FileHeaderField.UInt.opsLimit.write(header.hashData.operationsLimit, headerBytes)
        FileHeaderField.UInt.memLimit.write(header.hashData.hashingMemoryLimit, headerBytes)
        FileHeaderField.Boolean.isEncrypted.write(header.isEncrypted, headerBytes)
        FileHeaderField.UByteArray.chaCha20Poly1305Header.write(header.chaCha20Header, headerBytes)

        val remainingReservedSpaceSize = TOTAL_HEADER_SIZE - headerBytes.size
        repeat(remainingReservedSpaceSize.toInt()) {
            headerBytes.writeByte(0x00)
        }

        return headerBytes
    }

    override fun parseHeader(source: Source): ParseResult {
        val headerBytes = Buffer()
        source.read(headerBytes, TOTAL_HEADER_SIZE)
        val format = FileHeaderField.String.format.read(headerBytes)
        if (format != FORMAT_IDENTIFIER_MAGIC_NUMBER) return ParseResult.Failure.UnknownFormat(format)
        headerBytes.skip(SIZE_OF_GAP_AFTER_FORMAT_FIELD)

        val version = FileHeaderField.UShort.version.read(headerBytes).toInt()
        if (version !in SUPPORTED_VERSIONS) {
            return ParseResult.Failure.UnsupportedVersion(version)
        }

        val salt = FileHeaderField.UByteArray.salt.read(headerBytes)
        val hashedUserId = FileHeaderField.UByteArray.hashedUserId.read(headerBytes)
        val opsLimit = FileHeaderField.UInt.opsLimit.read(headerBytes)
        val memLimit = FileHeaderField.UInt.memLimit.read(headerBytes)
        val isEncrypted = FileHeaderField.Boolean.isEncrypted.read(headerBytes)
        val chaCha20HeaderData = FileHeaderField.UByteArray.chaCha20Poly1305Header.read(headerBytes)

        val hashData = HashData(hashedUserId, salt, opsLimit, memLimit)
        val header = BackupHeader(version, isEncrypted, hashData, chaCha20HeaderData)
        return ParseResult.Success(header)
    }

    private companion object {
        /**
         * The total amount of bytes reserved for the header in the beginning of the file.
         * Although the current fields occupy just around 100 bytes, we choose to reserve the first 1024 bytes for the header.
         * This way we can add extra fields in the future without breaking the format and requiring a file format version bump.
         */
        const val TOTAL_HEADER_SIZE = 1024L
        const val FORMAT_IDENTIFIER_MAGIC_NUMBER = "WBUX"

        /**
         * We leave an unreadable char in the beginning, so it isn't identified as a text-file by some software / OS
         */
        private const val SIZE_OF_GAP_AFTER_FORMAT_FIELD = 1L
        val SUPPORTED_VERSIONS = 4..4
    }
}
