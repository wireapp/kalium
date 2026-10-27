/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.common.error

import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.exceptions.ProteusException
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.model.AdminlessConversationErrorResponse
import com.wire.kalium.network.api.model.GenericAPIErrorResponse
import com.wire.kalium.network.api.model.MLSErrorResponse
import com.wire.kalium.network.api.model.QualifiedID
import com.wire.kalium.network.exceptions.AdminlessConversationError
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.MLSError
import com.wire.kalium.network.utils.NetworkResponse
import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FailureAdaptersTest {
    @Test
    fun givenRateLimitedResponse_whenWrapped_thenRetainsStatusLabelAndRetryDelay() {
        val exception = KaliumException.InvalidRequestError(GenericAPIErrorResponse(429, "wait", "rate-limit"), 37)

        val result = assertIs<Either.Left<NetworkFailure>>(wrapApiRequest { NetworkResponse.Error(exception) })
        val failure = assertIs<NetworkFailure.ServerMiscommunication>(result.value)
        val reason = assertIs<ServerError.InvalidRequest>(failure.reason)

        assertEquals(ServerError.Response(429, "wait", "rate-limit"), reason.errorResponse)
        assertEquals(37, reason.retryAfterSeconds)
        assertTrue(reason.isTooManyRequests())
        assertSame(exception, failure.rootCause)
    }

    @Test
    fun givenAdminlessConversation_whenWrapped_thenExposesDomainIds() {
        val exception = AdminlessConversationError(
            AdminlessConversationErrorResponse(403, "admin required", "adminless-conversation", listOf(QualifiedID("user", "domain")))
        )

        val reason = assertIs<ServerError.AdminlessConversation>(serverMiscommunication(exception).reason)

        assertEquals(ServerError.Response(403, "admin required", "adminless-conversation"), reason.errorResponse)
        assertEquals(listOf(UserId("user", "domain")), reason.eligibleMembers)
    }

    @Test
    fun givenProteusNativeCode_whenWrapped_thenRetainsPublicClassificationAndOriginalCause() {
        val exception = ProteusException("missing session", 2)

        val result = assertIs<Either.Left<ProteusFailure>>(wrapProteusRequest<Unit> { throw exception })

        assertEquals(ProteusFailure.Code.SESSION_NOT_FOUND, result.value.code)
        assertEquals(2, result.value.intCode)
        assertSame(exception, result.value.rootCause)
    }

    @Test
    fun givenMlsProtocolError_whenMappedAndTransported_thenPreservesLabelAndMessage() {
        val failure = mapMLSError(MLSError(MLSErrorResponse.ProtocolError("protocol detail")))

        assertEquals(
            NetworkFailure.MlsMessageRejectedFailure.Other(MlsErrorDetails("mls-protocol-error", "protocol detail")),
            failure
        )
        assertEquals(
            MLSFailure.MessageRejected(failure),
            MLSTransportFailureSerialization.parseString(MLSTransportFailureSerialization.serialize(failure))
        )
    }

    @Test
    fun givenLegacyMlsTransportPayload_whenParsed_thenUsesIndependentErrorDetails() {
        val payload = """
            {"type":"com.wire.kalium.common.error.MLSTransportFailure.MessageRejected",
             "reason":{"type":"com.wire.kalium.common.error.NetworkFailure.MlsMessageRejectedFailure.Other",
             "remoteCause":{"label":"mls-protocol-error","message":"legacy detail"}}}
        """.trimIndent()

        assertEquals(
            MLSFailure.MessageRejected(
                NetworkFailure.MlsMessageRejectedFailure.Other(MlsErrorDetails("mls-protocol-error", "legacy detail"))
            ),
            MLSTransportFailureSerialization.parseString(payload)
        )
    }

    @Test
    fun givenCancellation_whenWrappingCrypto_thenPropagatesCancellation() {
        val cancellation = CancellationException("cancelled")

        assertSame(cancellation, assertFailsWith<CancellationException> { wrapProteusRequest<Unit> { throw cancellation } })
        assertSame(cancellation, assertFailsWith<CancellationException> { wrapMLSRequest<Unit> { throw cancellation } })
    }
}
