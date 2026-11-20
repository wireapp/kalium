/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.data.conversation

import com.wire.kalium.common.error.MLSFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MLSMessageEpochTest {

    @Test
    fun givenPrivateMessage_whenReadingEpoch_thenReturnTheHeaderEpoch() {
        assertEquals(0x0102030405060708UL, mlsMessageEpoch(message(PRIVATE, byteArrayOf(3, 9, 9, 9), EPOCH_BYTES)))
    }

    @Test
    fun givenPublicMessageWithTwoByteGroupIdLength_whenReadingEpoch_thenReturnTheHeaderEpoch() {
        // 0x40 marks a 2-byte length: 0x0041 = 65 group ID bytes.
        val groupId = byteArrayOf(0x40, 0x41) + ByteArray(65)
        assertEquals(0x0102030405060708UL, mlsMessageEpoch(message(PUBLIC, groupId, EPOCH_BYTES)))
    }

    @Test
    fun givenWelcomeOrTruncatedMessage_whenReadingEpoch_thenReturnNull() {
        assertNull(mlsMessageEpoch(message(WELCOME, byteArrayOf(0), EPOCH_BYTES)))
        assertNull(mlsMessageEpoch(message(PRIVATE, byteArrayOf(3, 9, 9, 9), byteArrayOf(1, 2))))
        assertNull(mlsMessageEpoch(byteArrayOf()))
    }

    @Test
    fun givenDecryptFailure_whenCheckingPastEpoch_thenCompareHeaderWithLocalEpoch() {
        val failure = MLSFailure.Other(CORE_CRYPTO_DECRYPT_FAILURE)
        val message = message(PRIVATE, byteArrayOf(0), ByteArray(7) + 4)

        assertTrue(isPastEpochDecryptFailure(failure, message) { 5UL })
        assertFalse(isPastEpochDecryptFailure(failure, message) { 4UL })
        assertFalse(isPastEpochDecryptFailure(failure, message) { null })
        assertFalse(isPastEpochDecryptFailure(MLSFailure.Other("storage"), message) { 5UL })
    }

    private fun message(wireFormat: Byte, groupId: ByteArray, epoch: ByteArray) =
        byteArrayOf(0, 1, 0, wireFormat) + groupId + epoch

    private companion object {
        const val PUBLIC: Byte = 1
        const val PRIVATE: Byte = 2
        const val WELCOME: Byte = 3
        val EPOCH_BYTES = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
    }
}
