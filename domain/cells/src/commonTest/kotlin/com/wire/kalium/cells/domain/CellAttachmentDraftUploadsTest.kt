/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.cells.domain

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CellAttachmentDraftUploadsTest {
    @Test
    fun unclaimedOperationCannotStartAndOwnerFailureSettlesIt() = runTest {
        val registry = CellAttachmentDraftUploadsImpl(backgroundScope)
        registry.requireBackgroundOwner()
        var started = false
        val operation = registry.launch {
            started = true
            CellAttachmentDraftUploadResult.Completed
        }
        runCurrent()
        assertEquals(listOf(operation), registry.uploads.value)
        assertFalse(started)
        operation.cancelAndAwait()
        assertFalse(started)
        assertEquals(CellAttachmentDraftUploadResult.Cancelled, operation.awaitCompletion())
        assertTrue(registry.uploads.value.isEmpty())
    }

    @Test
    fun cancellationWaitsForLateWorkAndCleanupWithoutAffectingAnotherOperation() = runTest {
        val registry = CellAttachmentDraftUploadsImpl(backgroundScope)
        registry.requireBackgroundOwner()
        val lateNode = CompletableDeferred<Unit>()
        val transferAbort = CompletableDeferred<Unit>()
        var aborted = false
        val operation = registry.launch {
            try {
                withContext(NonCancellable) { lateNode.await() }
                awaitCancellation()
            } finally {
                withContext(NonCancellable) {
                    transferAbort.await()
                    aborted = true
                }
            }
        }
        val other = registry.launch { awaitCancellation() }
        operation.claim()
        other.claim()
        runCurrent()
        val cancellation = async { operation.cancelAndAwait() }
        runCurrent()
        assertFalse(cancellation.isCompleted)
        lateNode.complete(Unit)
        runCurrent()
        assertFalse(cancellation.isCompleted)
        assertFalse(aborted)
        transferAbort.complete(Unit)
        cancellation.await()
        assertTrue(aborted)
        assertEquals(CellAttachmentDraftUploadResult.Cancelled, operation.awaitCompletion())
        assertEquals(listOf(other), registry.uploads.value)
        assertTrue(other.job.isActive)
        other.cancelAndAwait()
    }
}
