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
package com.wire.backup.envelope.header

import okio.Buffer

internal interface FileHeaderField<Format : Any> {
    val sizeInBytes: Long
    fun read(input: Buffer): Format
    fun write(data: Format, output: Buffer)

    abstract class ArbitrarySizeHeaderField<Format : Any>(override val sizeInBytes: Long) : FileHeaderField<Format> {

        abstract fun fromBytes(bytes: ByteArray): Format
        abstract fun toBytes(data: Format): ByteArray

        override fun read(input: Buffer): Format = fromBytes(input.readByteArray(sizeInBytes))

        override fun write(data: Format, output: Buffer) {
            output.write(toBytes(data))
        }
    }

    class String private constructor(sizeInBytes: Long) : ArbitrarySizeHeaderField<kotlin.String>(sizeInBytes) {
        override fun toBytes(data: kotlin.String): ByteArray = data.encodeToByteArray()
        override fun fromBytes(bytes: ByteArray): kotlin.String = bytes.decodeToString()

        companion object {
            private const val FORMAT_SIZE_IN_BYTES = 4L
            val format = String(FORMAT_SIZE_IN_BYTES)
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    class UByteArray private constructor(sizeInBytes: Long) : ArbitrarySizeHeaderField<kotlin.UByteArray>(sizeInBytes) {
        override fun fromBytes(bytes: ByteArray): kotlin.UByteArray = bytes.toUByteArray()
        override fun toBytes(data: kotlin.UByteArray): ByteArray = data.toByteArray()

        companion object {
            private const val CHA_CHA_20_POLY_1305_HEADER_SIZE_IN_BYTES = 24L

            val salt = UByteArray(HashData.SALT_SIZE_IN_BYTES)
            val hashedUserId = UByteArray(HashData.HASHED_USER_ID_SIZE_IN_BYTES)
            val chaCha20Poly1305Header = UByteArray(CHA_CHA_20_POLY_1305_HEADER_SIZE_IN_BYTES)
        }
    }

    class Boolean private constructor() : FileHeaderField<kotlin.Boolean> {
        override val sizeInBytes: Long
            get() = 1L

        override fun read(input: Buffer): kotlin.Boolean = input.readByte() != 0x00.toByte()

        override fun write(data: kotlin.Boolean, output: Buffer) {
            output.writeByte(if (data) 0x01 else 0x00)
        }

        companion object {
            val isEncrypted = Boolean()
        }
    }

    class UInt private constructor() : FileHeaderField<kotlin.UInt> {
        override val sizeInBytes: Long
            get() = SIZE_IN_BYTES

        override fun read(input: Buffer): kotlin.UInt = input.readInt().toUInt()

        override fun write(data: kotlin.UInt, output: Buffer) {
            output.writeInt(data.toInt())
        }

        companion object {
            val opsLimit = UInt()
            val memLimit = UInt()

            /**
             * Amount of bytes used by an unsigned Integer when reading/writing to file
             */
            private const val SIZE_IN_BYTES = 4L
        }
    }

    class UShort private constructor() : FileHeaderField<kotlin.UShort> {
        override val sizeInBytes: Long
            get() = SIZE_IN_BYTES

        override fun read(input: Buffer): kotlin.UShort = input.readShort().toUShort()

        override fun write(data: kotlin.UShort, output: Buffer) {
            output.writeShort(data.toInt())
        }

        companion object {
            val version = UShort()

            /**
             * Amount of bytes used by an unsigned Short when reading/writing to file
             */
            private const val SIZE_IN_BYTES = 2L
        }
    }
}
