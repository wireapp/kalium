/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.backup.envelope

import okio.Buffer
import okio.Source

/**
 * Reads and writes a [BackupHeader] to data streams.
 *
 * See [the file specifications in backup/README.md](https://github.com/wireapp/kalium/blob/develop/backup/README.md)
 */
internal interface BackupHeaderSerializer {
    /**
     * Converts a [BackupHeader] into a byte buffer format, which can be stored in the beginning of a Backup file.
     */
    fun headerToBytes(header: BackupHeader): ByteArray

    /**
     * Consumes the first relevant bytes of the [source], parses and returns a [HeaderParseResult].
     */
    fun parseHeader(source: Source): HeaderParseResult

    companion object {
        /**
         * The total amount of bytes reserved for the header in the beginning of the file.
         * Although the current fields occupy just around 100 bytes, we choose to reserve the first 1024 bytes for the header.
         * This way we can add extra fields in the future without breaking the format and requiring a file format version bump.
         */
        const val HEADER_SIZE = 1024L
    }

    object Default : BackupHeaderSerializer {
        const val CURRENT_HEADER_VERSION = 4
        private const val FORMAT_IDENTIFIER_MAGIC_NUMBER = "WBUX"
        const val MINIMUM_SUPPORTED_VERSION = 4
        const val MAXIMUM_SUPPORTED_VERSION = 4
        val SUPPORTED_VERSIONS = MINIMUM_SUPPORTED_VERSION..MAXIMUM_SUPPORTED_VERSION

        /**
         * We leave an unreadable char in the beginning, so it isn't identified as a text-file by some software / OS
         */
        private const val SIZE_OF_GAP_AFTER_FORMAT_FIELD = 1L

        override fun headerToBytes(header: BackupHeader): ByteArray {
            val headerBytes = Buffer()
            BackupHeaderField.String.format.write(FORMAT_IDENTIFIER_MAGIC_NUMBER, headerBytes)
            repeat(SIZE_OF_GAP_AFTER_FORMAT_FIELD.toInt()) {
                headerBytes.writeByte(0x00)
            }
            BackupHeaderField.UShort.version.write(header.version.toUShort(), headerBytes)
            BackupHeaderField.UByteArray.salt.write(header.hashData.salt, headerBytes)
            BackupHeaderField.UByteArray.hashedUserId.write(header.hashData.hashedUserId, headerBytes)
            BackupHeaderField.UInt.opsLimit.write(header.hashData.operationsLimit.toUInt(), headerBytes)
            BackupHeaderField.UInt.memLimit.write(header.hashData.hashingMemoryLimit.toUInt(), headerBytes)
            BackupHeaderField.Boolean.isEncrypted.write(header.isEncrypted, headerBytes)

            val remainingReservedSpaceSize = HEADER_SIZE - headerBytes.size
            repeat(remainingReservedSpaceSize.toInt()) {
                headerBytes.writeByte(0x00)
            }

            return headerBytes.readByteArray()
        }

        override fun parseHeader(source: Source): HeaderParseResult {
            val headerBytes = Buffer()
            return if (source.read(headerBytes, HEADER_SIZE) != HEADER_SIZE) {
                HeaderParseResult.Failure.UnknownFormat
            } else {
                val format = BackupHeaderField.String.format.read(headerBytes)
                if (format != FORMAT_IDENTIFIER_MAGIC_NUMBER) return HeaderParseResult.Failure.UnknownFormat
                headerBytes.skip(SIZE_OF_GAP_AFTER_FORMAT_FIELD)
                val version = BackupHeaderField.UShort.version.read(headerBytes).toInt()
                if (version !in SUPPORTED_VERSIONS) {
                    HeaderParseResult.Failure.UnsupportedVersion(version)
                } else {
                    val salt = BackupHeaderField.UByteArray.salt.read(headerBytes)
                    val hashedUserId = BackupHeaderField.UByteArray.hashedUserId.read(headerBytes)
                    val opsLimit = BackupHeaderField.UInt.opsLimit.read(headerBytes)
                    val memLimit = BackupHeaderField.UInt.memLimit.read(headerBytes)
                    val isEncrypted = BackupHeaderField.Boolean.isEncrypted.read(headerBytes)

                    val hashData = HashData(hashedUserId, salt, opsLimit.toULong(), memLimit.toInt())
                    val header = BackupHeader(version, isEncrypted, hashData)
                    HeaderParseResult.Success(header)
                }
            }
        }
    }

}

internal sealed interface HeaderParseResult {
    data class Success(val header: BackupHeader) : HeaderParseResult
    sealed interface Failure : HeaderParseResult {
        /**
         * The file does not follow the expected format, by not starting with the correct magical numbers, or not having
         * the minimum expected size.
         */
        data object UnknownFormat : Failure

        /**
         * The [version] found in the backup is not supported. Either too old, or too new.
         */
        data class UnsupportedVersion(val version: Int) : Failure
    }
}
