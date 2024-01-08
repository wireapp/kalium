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

package com.wire.kalium.cryptography.utils

/**
 * Simple utility class that enables holding ByteArrays.
 * This implements equals and hash code.
 */
@Suppress("UnnecessaryAbstractClass")
abstract class ByteDataContainer(val data: ByteArray) {

    override fun hashCode(): Int {
        return data.contentHashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ByteDataContainer

        if (!data.contentEquals(other.data)) return false

        return true
    }
}

class SHA256Key(data: ByteArray) : ByteDataContainer(data)

class AES256Key(data: ByteArray) : ByteDataContainer(data)

class EncryptedData(data: ByteArray) : ByteDataContainer(data)

class PlainData(data: ByteArray) : ByteDataContainer(data)
