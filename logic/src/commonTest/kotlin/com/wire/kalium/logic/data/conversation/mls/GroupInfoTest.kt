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

package com.wire.kalium.logic.data.conversation.mls

import okio.ByteString.Companion.decodeBase64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GroupInfoTest {

    @Test
    fun givenValidGroupInfo_whenExtractingEpoch_thenReturnsCorrectEpochValue() {
        // Given: A valid base64-encoded GroupInfo structure
        val groupInfoBytes = decodeBase64(VALID_GROUP_INFO_BASE64)
        val groupInfo = GroupInfo(groupInfoBytes)

        // When: Extracting the epoch
        val epoch = groupInfo.extractEpoch()

        // Then: Epoch should be successfully extracted with value 1
        assertNotNull(epoch, "Epoch should not be null for valid GroupInfo")
        assertEquals(1L, epoch, "Epoch value should be 1")
    }

    @Test
    fun givenEmptyByteArray_whenExtractingEpoch_thenReturnsNull() {
        // Given: An empty byte array
        val groupInfo = GroupInfo(byteArrayOf())

        // When: Extracting the epoch
        val epoch = groupInfo.extractEpoch()

        // Then: Should return null due to insufficient data
        assertNull(epoch, "Epoch should be null for empty data")
    }

    @Test
    fun givenInsufficientData_whenExtractingEpoch_thenReturnsNull() {
        // Given: A byte array with insufficient data (less than 4 bytes for version and cipher suite)
        val groupInfo = GroupInfo(byteArrayOf(0x00, 0x01))

        // When: Extracting the epoch
        val epoch = groupInfo.extractEpoch()

        // Then: Should return null
        assertNull(epoch, "Epoch should be null for insufficient data")
    }

    @Test
    fun givenNonMinimalVarint_whenExtractingEpoch_thenReturnsNull() {
        // Given: A byte array with non-minimal MLS varint encoding
        // A 2-byte varint encoding a value less than 64 (should use 1-byte encoding)
        val nonMinimalData = byteArrayOf(
            0x00, 0x01, // version
            0x00, 0x01, // cipher suite
            0x40, 0x00  // varint: prefix 01 encoding value 0 (should be 0x00 for minimal)
        )
        val groupInfo = GroupInfo(nonMinimalData)

        // When: Extracting the epoch
        val epoch = groupInfo.extractEpoch()

        // Then: Should return null due to non-minimal encoding
        assertNull(epoch, "Epoch should be null for non-minimal varint")
    }

    @Test
    fun givenDataTruncatedAfterGroupId_whenExtractingEpoch_thenReturnsNull() {
        // Given: Valid structure up to group_id but missing epoch field
        val truncatedData = byteArrayOf(
            0x00, 0x01, // version
            0x00, 0x01, // cipher suite
            0x04,       // group_id length = 4
            0x01, 0x02, 0x03, 0x04 // group_id bytes (but epoch is missing)
        )
        val groupInfo = GroupInfo(truncatedData)

        // When: Extracting the epoch
        val epoch = groupInfo.extractEpoch()

        // Then: Should return null due to missing epoch
        assertNull(epoch, "Epoch should be null when epoch field is missing")
    }

    @Test
    fun givenLargeEpochValue_whenExtractingEpoch_thenReturnsCorrectValue() {
        // Given: GroupInfo with a large epoch value (Long.MAX_VALUE)
        val largeEpochData = byteArrayOf(
            0x00, 0x01, // version = 1
            0x00, 0x01, // cipher suite = 1
            0x01,       // group_id length = 1
            0xFF.toByte(), // group_id byte
            0x7F, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte() // epoch = Long.MAX_VALUE
        )
        val groupInfo = GroupInfo(largeEpochData)

        // When: Extracting the epoch
        val epoch = groupInfo.extractEpoch()

        // Then: Should return Long.MAX_VALUE
        assertNotNull(epoch, "Epoch should not be null")
        assertEquals(Long.MAX_VALUE, epoch, "Epoch value should be Long.MAX_VALUE")
    }

    @Test
    fun givenGroupInfoWithLargeGroupId_whenExtractingEpoch_thenReturnsEpoch() {
        // Given: GroupInfo with a larger group_id (using 2-byte varint encoding)
        val largeGroupId = ByteArray(100) { it.toByte() }
        val dataWithLargeGroupId = byteArrayOf(
            0x00, 0x01, // version = 1
            0x00, 0x01, // cipher suite = 1
            0x40, 0x64  // group_id length = 100 (2-byte varint: prefix 01, value 100)
        ) + largeGroupId + byteArrayOf(
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x05 // epoch = 5
        )
        val groupInfo = GroupInfo(dataWithLargeGroupId)

        // When: Extracting the epoch
        val epoch = groupInfo.extractEpoch()

        // Then: Should successfully extract epoch
        assertNotNull(epoch, "Epoch should not be null")
        assertEquals(5L, epoch, "Epoch value should be 5")
    }

    companion object {
        /**
         * Valid base64-encoded GroupInfo structure with epoch = 1
         */
        private const val VALID_GROUP_INFO_BASE64 = "AAEAAh0AAQAA4TIPMAv7QpeT33H7mYypjgB3aXJlLmNvbQAAAAAAAAABILQpw" +
                "PavtNVpXXY9NzYwIL8cMD+LtTUjr/mghAwwR+OYIP7auVHxJIa0Qg6bOHPtxGKR4Y8MPcwdDz0esrib90JWQGEAAwcAAAQAAQACA" +
                "AVAU0BRQEUE1ReEtgyhHW8nRN4rMotYBHijBtJxjcWjaXJJQ0OEguTMvGgqWqTrclCmw/zuzMI0oJ7SFNohQ6+u4WwzElyiOgABC" +
                "3dpcmUtc2VydmVyR6oAAkdfR10BAUBBBPP9/NpHWJdjXtXmvGHQyAIT3swfIEARytf7dg1L5sQZMaC1guNBGuwvi/cWK0I9hctvV" +
                "EY1PzyM1P8+jOYOHHhAQQQBVyv9PObXTGSmxupZpz+aiMC3hgSex0rPckOwj1CG/5s1a8v6izXm3ntooHOA/jlvOuUMpRWsekWTn" +
                "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"

        /**
         * Decodes a base64 string to a byte array.
         * Uses Okio's ByteString for base64 decoding
         */
        private fun decodeBase64(base64String: String): ByteArray {
            // Remove any whitespace
            val cleanedString = base64String.replace("\\s".toRegex(), "")

            // Decode using okio ByteString extension
            return cleanedString.decodeBase64()!!.toByteArray()
        }
    }
}
