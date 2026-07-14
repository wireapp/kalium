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

package com.wire.kalium.util.string

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StringExtTest {

    @Test
    fun givenByteArray_whenConvertingToHexString_thenResultIsLowercaseHex() {
        val bytes = byteArrayOf(0x01, 0x02, 0xFF.toByte())
        assertEquals("0102ff", bytes.toHexString())
    }

    @Test
    fun givenHexString_whenConvertingToByteArray_thenResultMatchesOriginalBytes() {
        assertContentEquals(byteArrayOf(0x01, 0x02, 0xFF.toByte()), "0102ff".hexToByteArray())
    }

    @Test
    fun givenByteArray_whenRoundTrippingThroughHex_thenResultIsIdentical() {
        val original = byteArrayOf(1, 2, 3, 0, 127, -1, -128)
        assertContentEquals(original, original.toHexString().hexToByteArray())
    }

    @Test
    fun givenEmptyString_whenConvertingToByteArray_thenResultIsEmpty() {
        assertContentEquals(ByteArray(0), "".hexToByteArray())
    }

    @Test
    fun givenOddLengthHexString_whenConvertingToByteArray_thenThrowsIllegalArgumentException() {
        assertFailsWith<IllegalArgumentException> { "abc".hexToByteArray() }
    }
}
