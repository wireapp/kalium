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

package com.wire.kalium.util

import com.wire.kalium.util.int.toByteArray
import com.wire.kalium.util.int.toHexString
import com.wire.kalium.util.long.toByteArray
import com.wire.kalium.util.string.toHexString
import kotlin.test.Test
import kotlin.test.assertEquals

class IntExtTests {

    @Test
    fun givenMaxLongValue_whenConvertingToByteArray_HexStringIsEqualToTheExpected() {
        val result = 9_223_372_036_854_775_803.toByteArray()
        assertEquals("7ffffffffffffffb", result.toHexString())
    }

    @Test
    fun givenA1AsDecimal_whenConvertingToByteArray_HexStringIsEqualToTheExpected() {
        val result = 1.toByteArray()
        assertEquals("00000001", result.toHexString().uppercase())
    }

    @Test
    fun givenA10AsDecimal_whenConvertingToByteArray_HexStringIsEqualToTheExpected() {
        val result = 10.toByteArray()
        assertEquals("0000000A", result.toHexString().uppercase())
    }

    @Test
    fun givenA100AsDecimal_whenConvertingToByteArray_HexStringIsEqualToTheExpected() {
        val result = 100.toByteArray()
        assertEquals("00000064", result.toHexString().uppercase())
    }

    @Test
    fun givenA1000AsDecimal_whenConvertingToByteArray_HexStringIsEqualToTheExpected() {
        val result = 1000.toByteArray()
        assertEquals("000003E8", result.toHexString().uppercase())
    }

    @Test
    fun givenA10000AsDecimal_whenConvertingToByteArray_HexStringIsEqualToTheExpected() {
        val result = 10000.toByteArray()
        assertEquals("00002710", result.toHexString().uppercase())
    }

    @Test
    fun givenA1_00_00_00_00_00AsDecimal_whenConvertingToByteArray_HexStringIsEqualToTheExpected() {
        val result = 1_00_00_00_00_00.toByteArray()
        assertEquals("00000002540BE400", result.toHexString().uppercase())
    }

    @Test
    fun givenAnInteger_whenConvertingToHex_HexValueIsAsExpected(){
        val given = 2
        val expected= "0x000$given"
        assertEquals(expected, given.toHexString())
    }
}
