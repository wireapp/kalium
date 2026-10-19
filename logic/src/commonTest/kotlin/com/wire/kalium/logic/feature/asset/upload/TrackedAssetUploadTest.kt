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
package com.wire.kalium.logic.feature.asset.upload

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TrackedAssetUploadTest {
    @Test
    fun givenPendingSend_whenAwaitingCompletion_thenWaitForSendAndMonitorCleanup() = runTest {
        val send = CompletableDeferred<Unit>()
        val monitorCleanup = CompletableDeferred<Unit>()
        var monitorCleaned = false
        val operation = TrackedAssetUploadImpl(
            messageId = "message",
            scope = this,
            retryOperation = { error("Unexpected retry") },
            monitor = {
                try {
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) { monitorCleanup.await() }
                    monitorCleaned = true
                }
            },
            upload = { send.await(); Unit.right() },
            onCancelled = {},
        )
        val completion = async { operation.awaitCompletion() }
        runCurrent()
        assertEquals(AssetUploadProgress.UPLOADING_AND_SENDING, operation.progress.value)
        assertFalse(completion.isCompleted)

        send.complete(Unit)
        runCurrent()
        assertFalse(completion.isCompleted)
        monitorCleanup.complete(Unit)

        assertEquals(AssetUploadCompletion.Success, completion.await())
        assertTrue(monitorCleaned)
        assertEquals(AssetUploadProgress.COMPLETED, operation.progress.value)
    }

    @Test
    fun givenSendSucceeded_whenCancelledDuringMonitorCleanup_thenPreserveSuccessAndAwaitCleanup() = runTest {
        val send = CompletableDeferred<Unit>()
        val monitorCleanup = CompletableDeferred<Unit>()
        var cancelledStatusUpdates = 0
        var monitorCleaned = false
        val operation = TrackedAssetUploadImpl(
            messageId = "sent-message",
            scope = this,
            retryOperation = { error("Successful sends must not be retried") },
            monitor = {
                try {
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) { monitorCleanup.await() }
                    monitorCleaned = true
                }
            },
            upload = { send.await(); Unit.right() },
            onCancelled = { cancelledStatusUpdates++ },
        )
        val completion = async { operation.awaitCompletion() }
        send.complete(Unit)
        runCurrent()
        operation.cancel()
        runCurrent()
        assertFalse(completion.isCompleted)
        monitorCleanup.complete(Unit)

        assertEquals(AssetUploadCompletion.Success, completion.await())
        assertTrue(monitorCleaned)
        assertEquals(0, cancelledStatusUpdates)
        assertEquals(AssetUploadProgress.COMPLETED, operation.progress.value)
        assertSame(operation, operation.retry())
    }

    @Test
    fun givenParallelUploads_whenCancellingOne_thenOtherUploadContinuesAndCleanupIsAwaited() = runTest {
        val firstSend = CompletableDeferred<Unit>()
        val secondSend = CompletableDeferred<Unit>()
        val cancellationCleanup = CompletableDeferred<Unit>()
        var firstMonitorCleaned = false
        var secondMonitorCleaned = false
        val first = TrackedAssetUploadImpl(
            messageId = "first",
            scope = this,
            retryOperation = { error("Unexpected retry") },
            monitor = { try { awaitCancellation() } finally { firstMonitorCleaned = true } },
            upload = { firstSend.await(); Unit.right() },
            onCancelled = { cancellationCleanup.await() },
        )
        val second = TrackedAssetUploadImpl(
            messageId = "second",
            scope = this,
            retryOperation = { error("Unexpected retry") },
            monitor = { try { awaitCancellation() } finally { secondMonitorCleaned = true } },
            upload = { secondSend.await(); Unit.right() },
            onCancelled = {},
        )
        assertNotEquals(first.operationId, second.operationId)
        first.cancel()
        val cancelled = async { first.awaitCompletion() }
        runCurrent()
        assertFalse(cancelled.isCompleted)
        assertTrue(firstMonitorCleaned)
        assertFalse(secondMonitorCleaned)
        assertEquals(AssetUploadProgress.UPLOADING_AND_SENDING, second.progress.value)
        cancellationCleanup.complete(Unit)
        assertEquals(AssetUploadCompletion.Cancelled, cancelled.await())
        assertEquals(AssetUploadProgress.CANCELLED, first.progress.value)

        secondSend.complete(Unit)
        assertEquals(AssetUploadCompletion.Success, second.awaitCompletion())
        assertTrue(secondMonitorCleaned)
    }

    @Test
    fun givenUploadFails_whenCompleted_thenFailureIsReturnedAndMonitorIsRemoved() = runTest {
        val failure = CoreFailure.Unknown(null)
        var monitorCleaned = false
        val operation = TrackedAssetUploadImpl(
            messageId = "message",
            scope = this,
            retryOperation = { error("Unexpected retry") },
            monitor = { try { awaitCancellation() } finally { monitorCleaned = true } },
            upload = { failure.left() },
            onCancelled = {},
        )
        assertEquals(AssetUploadCompletion.Failure(failure), operation.awaitCompletion())
        assertEquals(AssetUploadProgress.FAILED, operation.progress.value)
        assertTrue(monitorCleaned)
    }

    @Test
    fun givenMessageDeleted_whenMonitorCompletes_thenOnlyItsUploadIsCancelled() = runTest {
        val deleted = CompletableDeferred<Unit>()
        var uploadCancelled = false
        val operation = TrackedAssetUploadImpl(
            messageId = "message",
            scope = this,
            retryOperation = { error("Unexpected retry") },
            monitor = { deleted.await() },
            upload = { try { awaitCancellation() } finally { uploadCancelled = true } },
            onCancelled = {},
        )
        deleted.complete(Unit)
        assertEquals(AssetUploadCompletion.Cancelled, operation.awaitCompletion())
        assertTrue(uploadCancelled)
    }

    @Test
    fun givenFailedAttempt_whenRetryRequestedTwice_thenOnlyOneNewAttemptIsCreated() = runTest {
        var retryCount = 0
        val next = TrackedAssetUploadImpl(
            messageId = "same-message",
            scope = this,
            retryOperation = { error("Unexpected retry") },
            monitor = { awaitCancellation() },
            upload = { Unit.right() },
            onCancelled = {},
        )
        val first = TrackedAssetUploadImpl(
            messageId = "same-message",
            scope = this,
            retryOperation = { retryCount++; next },
            monitor = { awaitCancellation() },
            upload = { CoreFailure.Unknown(null).left() },
            onCancelled = {},
        )
        first.awaitCompletion()
        assertSame(next, first.retry())
        assertSame(next, first.retry())
        assertEquals(1, retryCount)
        assertEquals(first.messageId, next.messageId)
        assertNotEquals(first.operationId, next.operationId)
        assertEquals(AssetUploadCompletion.Success, next.awaitCompletion())
        assertSame(next, next.retry())
    }
}
