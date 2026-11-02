/* Wire, Copyright (C) 2026 Wire Swiss GmbH. SPDX-License-Identifier: GPL-3.0-only */
package com.wire.kalium.logic.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SyncRetryControllerTest {
    @Test
    fun manualRefreshWakesTheExistingWaitAndCancelsItsNetworkChild() = runTest {
        val controller = SyncRetryController()
        var resetCount = 0
        var networkCancelled = false
        val generation = controller.generation()
        val wait = async {
            controller.awaitRetry(generation, {}) {
                try { awaitCancellation() } finally { networkCancelled = true }
            }
        }
        runCurrent()
        controller.retryNow { resetCount++ }
        controller.retryNow { resetCount++ }
        assertEquals(RetryTrigger.USER_REQUESTED, wait.await())
        assertTrue(networkCancelled)
        assertEquals(2, resetCount)
        val network = CompletableDeferred<Boolean>()
        val nextGeneration = controller.generation()
        val next = async { controller.awaitRetry(nextGeneration, {}) { network.await() } }
        runCurrent()
        assertFalse(next.isCompleted) // Refreshes never remain queued for a future failure.
        network.complete(false)
        assertEquals(RetryTrigger.RETRY_DELAY_ELAPSED, next.await())
    }

    @Test
    fun refreshDuringRecoveryBeforeRegistrationIsNotLost() = runTest {
        val controller = SyncRetryController()
        val failureGeneration = controller.generation()
        controller.retryNow {}
        val result = controller.awaitRetry(failureGeneration, {}) { awaitCancellation() }
        assertEquals(RetryTrigger.USER_REQUESTED, result)
    }

    @Test
    fun refreshDuringSchedulePublicationWakesThatWait() = runTest {
        val controller = SyncRetryController()
        val generation = controller.generation()
        val result = controller.awaitRetry(generation, { controller.retryNow {} }) { awaitCancellation() }
        assertEquals(RetryTrigger.USER_REQUESTED, result)
    }

    @Test
    fun cancellingAnOldWaitDoesNotUnregisterTheNextWait() = runTest {
        val controller = SyncRetryController()
        val generation = controller.generation()
        val old = async { controller.awaitRetry(generation, {}) { awaitCancellation() } }
        runCurrent()
        old.cancel()
        val next = async { controller.awaitRetry(generation, {}) { awaitCancellation() } }
        runCurrent()
        old.join()
        controller.retryNow {}
        assertEquals(RetryTrigger.USER_REQUESTED, next.await())
    }
}
