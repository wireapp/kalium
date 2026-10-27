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

import kotlin.test.Test
import kotlin.test.assertEquals

class MLSTransportFailureSerializationTest {

    @Test
    fun givenConflictingBackendsFailure_whenRoundTripped_thenPreservesDomains() {
        val failure = NetworkFailure.FederatedBackendFailure.ConflictingBackends(
            listOf("backend-a.example", "backend-b.example")
        )

        val parsed = MLSTransportFailureSerialization.parseString(
            MLSTransportFailureSerialization.serialize(failure)
        )

        assertEquals(MLSFailure.FederatedBackendConflict(failure.domains), parsed)
        assertEquals(failure, parsed.normalizeFederatedBackendConflict())
    }

    @Test
    fun givenMessageRejectedFailure_whenRoundTripped_thenPreservesReason() {
        val failure = NetworkFailure.MlsMessageRejectedFailure.InvalidLeafNodeSignature

        val parsed = MLSTransportFailureSerialization.parseString(
            MLSTransportFailureSerialization.serialize(failure)
        )

        assertEquals(MLSFailure.MessageRejected(failure), parsed)
    }

    @Test
    fun givenGenericFailure_whenRoundTripped_thenFallsBackToOther() {
        val failure = CoreFailure.Unknown(IllegalStateException("generic failure"))

        val parsed = MLSTransportFailureSerialization.parseString(
            MLSTransportFailureSerialization.serialize(failure)
        )

        assertEquals(MLSFailure.Other(failure.toString()), parsed)
    }
}
