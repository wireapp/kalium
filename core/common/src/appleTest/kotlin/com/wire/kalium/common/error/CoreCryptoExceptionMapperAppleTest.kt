/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.common.error

import com.wire.crypto.CoreCryptoException
import com.wire.crypto.MlsException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CoreCryptoExceptionMapperAppleTest {

    @Test
    fun givenOrphanWelcomeException_whenCommonizing_thenMapsToOrphanWelcomeFailure() {
        val result = commonizeMLSException(CoreCryptoException.Mls(MlsException.OrphanWelcome()))

        assertEquals(MLSFailure.OrphanWelcome, result.failure)
        assertIs<CoreCryptoException.Mls>(result.cause)
    }

    @Test
    fun givenConversationNotFoundOtherException_whenCommonizing_thenMapsToConversationNotFoundFailure() {
        val result = commonizeMLSException(
            CoreCryptoException.Mls(MlsException.Other("Couldn't find conversation for orphan welcome replay"))
        )

        assertEquals(MLSFailure.ConversationNotFound, result.failure)
        assertIs<CoreCryptoException.Mls>(result.cause)
    }

    @Test
    fun givenMessageRejectedException_whenCommonizing_thenPreservesRejectedReason() {
        val serializedReason = MLSTransportFailureSerialization.serialize(
            NetworkFailure.MlsMessageRejectedFailure.InvalidLeafNodeSignature
        )

        val result = commonizeMLSException(CoreCryptoException.Mls(MlsException.MessageRejected(serializedReason)))

        assertEquals(
            MLSFailure.MessageRejected(NetworkFailure.MlsMessageRejectedFailure.InvalidLeafNodeSignature),
            result.failure,
        )
        assertIs<CoreCryptoException.Mls>(result.cause)
    }
}
