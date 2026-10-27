/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.common.error

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerErrorPredicatesTest {
    @Test
    fun givenHttpStatusAndLabels_whenClassifying_thenRetainsTheirDistinctMeanings() {
        assertTrue(request(404, "different-label").isNotFound())
        assertFalse(request(400, "not-found").isNotFound())
        assertTrue(request(400, "not-found").isNotFoundLabel())
        assertTrue(request(429, "different-label").isTooManyRequests())
        assertTrue(request(420, "different-label").isTooManyRequests())
        assertFalse(request(400, "rate-limit").isTooManyRequests())
        assertTrue(request(404, "meeting-not-found").isMeetingNotFound())
        assertFalse(request(400, "meeting-not-found").isMeetingNotFound())
    }

    @Test
    fun givenLegalHoldFailureWithoutTransportCause_whenClassifying_thenRemainsRetryable() {
        val failure: CoreFailure = NetworkFailure.ServerMiscommunication(request(403, "missing-legalhold-consent"))

        assertTrue(failure.isInvalidRequestError)
        assertTrue(failure.isMissingLegalHoldConsentError)
        assertTrue(failure.isRetryable)
        assertFalse(NetworkFailure.ServerMiscommunication(ServerError.Other(null)).isRetryable)
    }

    @Test
    fun givenAuthenticationLabels_whenClassifying_thenDistinguishesMissingAndInvalidCodes() {
        assertEquals(AuthenticationCodeFailure.MISSING_AUTHENTICATION_CODE,
            request(403, "code-authentication-required").authenticationCodeFailure)
        assertEquals(AuthenticationCodeFailure.INVALID_OR_EXPIRED_AUTHENTICATION_CODE,
            request(403, "code-authentication-failed").authenticationCodeFailure)
        assertEquals(null, request(403, "other").authenticationCodeFailure)
    }

    @Test
    fun givenLegacyPasswordResponse_whenClassifying_thenPreservesMessageFallback() {
        assertTrue(request(400, "bad-request", "incorrect password").isWrongConversationPassword())
        assertTrue(request(403, "invalid-conversation-password").isWrongConversationPassword())
        assertFalse(request(400, "bad-request", "other validation error").isWrongConversationPassword())
    }

    private fun request(code: Int, label: String, message: String = "") =
        ServerError.InvalidRequest(ServerError.Response(code, message, label))
}
