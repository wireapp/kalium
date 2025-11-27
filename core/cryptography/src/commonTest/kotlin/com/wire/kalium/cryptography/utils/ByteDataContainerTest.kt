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
