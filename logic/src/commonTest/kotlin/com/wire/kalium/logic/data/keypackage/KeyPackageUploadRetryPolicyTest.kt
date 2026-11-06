/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.data.keypackage

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.network.api.model.GenericAPIErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class KeyPackageUploadRetryPolicyTest {

    @Test
    fun givenTransientFailures_whenWaitingToRetry_thenUsesExponentialBackoff() = runTest {
        val delays = mutableListOf<Duration>()
        val policy = policyRecordingDelays(delays)
        val failure = clientFailure(429)

        assertTrue(policy.waitBeforeRetry(failure, failedAttempt = 1))
        assertTrue(policy.waitBeforeRetry(failure, failedAttempt = 2))

        assertEquals(listOf(1.seconds, 2.seconds), delays)
    }

    @Test
    fun givenRetryAfterWithinBound_whenWaitingToRetry_thenUsesServerDelay() = runTest {
        val delays = mutableListOf<Duration>()
        val policy = policyRecordingDelays(delays)
        val failure = clientFailure(429, retryAfterSeconds = 5)

        assertTrue(policy.waitBeforeRetry(failure, failedAttempt = 1))

        assertEquals(listOf(5.seconds), delays)
    }

    @Test
    fun givenRetryAfterOutsideBound_whenWaitingToRetry_thenDefersToLaterTrigger() = runTest {
        val delays = mutableListOf<Duration>()
        val policy = policyRecordingDelays(delays)
        val failure = clientFailure(429, retryAfterSeconds = 9)

        assertFalse(policy.waitBeforeRetry(failure, failedAttempt = 1))

        assertEquals(emptyList(), delays)
    }

    @Test
    fun whenWaitingBetweenBatches_thenUsesConfiguredPacingDelay() = runTest {
        val delays = mutableListOf<Duration>()
        val policy = policyRecordingDelays(delays)

        policy.waitBeforeNextBatch()

        assertEquals(listOf(250.milliseconds), delays)
    }

    private fun policyRecordingDelays(delays: MutableList<Duration>) = KeyPackageUploadRetryPolicy(
        jitterSource = { 0.0 },
        delayFunction = { delays += it },
    )

    private fun clientFailure(code: Int, retryAfterSeconds: Int? = null) = NetworkFailure.ServerMiscommunication(
        KaliumException.InvalidRequestError(
            GenericAPIErrorResponse(code, "client error", "client-error"),
            retryAfterSeconds,
        )
    )
}
