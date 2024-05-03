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

package com.wire.kalium.util.long

/**
 * Converts a Long into a Byte Array Big Endian.
 */
@Suppress("MagicNumber")
fun Long.toByteArray(): ByteArray {
    val result = ByteArray(Long.SIZE_BYTES)

    var longConvertedToByteArray = this

    for (i in Long.SIZE_BYTES - 1 downTo 0) {
        result[i] = (longConvertedToByteArray and 0xFFL).toByte()
        longConvertedToByteArray = longConvertedToByteArray shr Long.SIZE_BYTES
    }

    return result
}
