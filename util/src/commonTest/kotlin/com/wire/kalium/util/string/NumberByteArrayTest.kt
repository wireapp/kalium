package com.wire.kalium.util.string

import com.wire.kalium.util.int.toByteArray
import com.wire.kalium.util.long.toByteArray
import kotlin.test.Test
import kotlin.test.assertEquals

class NumberByteArrayTest {

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

}
