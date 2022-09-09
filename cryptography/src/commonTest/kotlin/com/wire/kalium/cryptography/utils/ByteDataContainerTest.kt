package com.wire.kalium.cryptography.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class ByteDataContainerTest {

    @Test
    fun givenTwoSHA256KeysWithEqualContent_whenComparingThem_thenTheResultShouldBeTrue() {
        val byteData = TEST_DATA

        assertEquals(SHA256Key(byteData), SHA256Key(byteData.copyOf()))
    }

    @Test
    fun givenTwoAES256KeysWithEqualContent_whenComparingThem_thenTheResultShouldBeTrue() {
        val byteData = TEST_DATA

        assertEquals(AES256Key(byteData), AES256Key(byteData.copyOf()))
    }

    @Test
    fun givenTwoEncryptedDataInstancesWithEqualContent_whenComparingThem_thenTheResultShouldBeTrue() {
        val byteData = TEST_DATA

        assertEquals(EncryptedData(byteData), EncryptedData(byteData.copyOf()))
    }

    @Test
    fun givenTwoPlainDataInstancesWithEqualContent_whenComparingThem_thenTheResultShouldBeTrue() {
        val byteData = TEST_DATA

        assertEquals(PlainData(byteData), PlainData(byteData.copyOf()))
    }

    companion object {
        private val TEST_DATA = byteArrayOf(0x12, 0x24, 0x32, 0x42)
    }
}
