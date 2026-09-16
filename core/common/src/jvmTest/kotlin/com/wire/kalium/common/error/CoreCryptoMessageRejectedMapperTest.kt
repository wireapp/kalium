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

package com.wire.kalium.common.error

import com.wire.crypto.CoreCryptoException
import com.wire.crypto.MlsException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class CoreCryptoMessageRejectedMapperTest {

    @Test
    fun givenSerializedMessageRejectedReason_whenCommonizing_thenMapsStructuredFailure() {
        val serializedReason = MLSTransportFailureSerialization.serialize(
            NetworkFailure.MlsMessageRejectedFailure.InvalidLeafNodeSignature
        )

        val result = commonizeMLSException(
            CoreCryptoException.Mls(MlsException.MessageRejected(serializedReason))
        )

        assertEquals(
            MLSFailure.MessageRejected(NetworkFailure.MlsMessageRejectedFailure.InvalidLeafNodeSignature),
            result.failure
        )
    }

    @Test
    fun givenPlainTextMessageRejectedReason_whenCommonizing_thenPreservesOriginalFailure() {
        val reason = "transport callback failed before serialization"
        val original = CoreCryptoException.Mls(MlsException.MessageRejected(reason))

        val result = commonizeMLSException(original)

        assertEquals(MLSFailure.Other(reason), result.failure)
        assertSame(original, result.cause)
    }
}
