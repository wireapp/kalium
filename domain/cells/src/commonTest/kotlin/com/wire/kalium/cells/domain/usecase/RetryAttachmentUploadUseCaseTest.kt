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
package com.wire.kalium.cells.domain.usecase

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import com.wire.kalium.cells.domain.CellAttachmentDraftUploadResult
import com.wire.kalium.cells.domain.CellAttachmentDraftUploadsImpl
import com.wire.kalium.cells.domain.CellUploadEvent
import com.wire.kalium.cells.domain.CellUploadAttempt
import com.wire.kalium.cells.domain.CellUploadInfo
import com.wire.kalium.cells.domain.CellUploadManager
import com.wire.kalium.cells.domain.MessageAttachmentDraftRepository
import com.wire.kalium.cells.domain.model.AttachmentDraft
import com.wire.kalium.cells.domain.model.AttachmentUploadStatus
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.isLeft
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import dev.mokkery.matcher.any
import dev.mokkery.everySuspend
import dev.mokkery.verify
import dev.mokkery.verifySuspend
import dev.mokkery.verify.VerifyMode
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class RetryAttachmentUploadUseCaseTest {

    private companion object {
        private const val attachmentId = "attachmentId"
    }

    @Test
    fun given_FailedUpload_when_RetryInvoked_and_StatusUpdateFails_then_ErrorReturned() = runTest {

        val (_, useCase) = Arrangement()
            .withStatusUpdateFailure()
            .arrange()

        val result = useCase(attachmentId)

        assertTrue { result.isLeft() }
    }

    @Test
    fun given_FailedUpload_when_RetryInvoked_then_RetryUploadCalled() = runTest {

        val (arrangement, useCase) = Arrangement()
            .withStatusUpdateSuccess()
            .arrange()

        useCase(attachmentId)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.uploadManager.retryUpload(attachmentId)
        }
    }

    @Test
    fun given_RetryInvoked_when_UploadCompleteReceived_then_StatusUpdated() = runTest {

        val (arrangement, useCase) = Arrangement()
            .withStatusUpdateSuccess()
            .arrange()

        useCase(attachmentId)
        advanceTimeBy(1)
        arrangement.uploadEventsFlow.emit(CellUploadEvent.UploadCompleted)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.repository.updateStatus(attachmentId, AttachmentUploadStatus.UPLOADED)
        }
    }

    @Test
    fun nativeRetryWaitsForOwnerAndUsesPersistedIdentityAfterExpiration() = runTest {
        val drafts = CellAttachmentDraftUploadsImpl(backgroundScope).also { it.requireBackgroundOwner() }
        val (arrangement, useCase) = Arrangement(backgroundScope, drafts).withStatusUpdateSuccess().arrange()
        val draft = AttachmentDraft(
            uuid = attachmentId, versionId = "version", fileName = "file", remoteFilePath = "cell/file",
            localFilePath = "/tmp/file", fileSize = 1, uploadStatus = AttachmentUploadStatus.FAILED,
            mimeType = "text/plain", assetWidth = null, assetHeight = null, assetDuration = null,
        )
        everySuspend { arrangement.repository.get(attachmentId) }.returns(draft.right())
        every { arrangement.uploadManager.retryDraftUpload(draft) }.returns(true)
        val attempt = mock<CellUploadAttempt>(mode = MockMode.autoUnit)
        every { arrangement.uploadManager.captureUploadAttempt(attachmentId) }.returns(attempt)
        every { arrangement.uploadManager.getUploadInfo(attachmentId) }.returns(CellUploadInfo())
        every { arrangement.uploadManager.consumeUploadOutcome(attachmentId) }.returns(null)
        val caller = async { useCase(attachmentId) }
        runCurrent()
        val operation = drafts.uploads.value.single()
        verify(VerifyMode.exactly(0)) { arrangement.uploadManager.retryDraftUpload(draft) }
        operation.claim()
        runCurrent()
        caller.await()
        assertTrue(useCase(attachmentId).isLeft())
        verifySuspend(VerifyMode.exactly(0)) { attempt.cancelAndAwait() }
        verifySuspend(VerifyMode.exactly(0)) { arrangement.repository.updateStatus(attachmentId, AttachmentUploadStatus.FAILED) }
        assertEquals(1, drafts.uploads.value.size)
        launch { arrangement.uploadEventsFlow.emit(CellUploadEvent.UploadCompleted) }
        runCurrent()
        assertEquals(CellAttachmentDraftUploadResult.Completed, operation.awaitCompletion())
        verify(VerifyMode.exactly(1)) { arrangement.uploadManager.retryDraftUpload(draft) }
        verifySuspend(VerifyMode.exactly(1)) { attempt.awaitStopped() }
    }

    @Test
    fun cancellingUnclaimedNativeRetryNeverStartsAnotherTransfer() = runTest {
        val drafts = CellAttachmentDraftUploadsImpl(backgroundScope).also { it.requireBackgroundOwner() }
        val (arrangement, useCase) = Arrangement(backgroundScope, drafts).arrange()
        val caller = async { useCase(attachmentId) }
        runCurrent()
        val operation = drafts.uploads.value.single()
        operation.cancelAndAwait()
        runCurrent()
        assertTrue(caller.await().isLeft())
        assertEquals(CellAttachmentDraftUploadResult.Cancelled, operation.awaitCompletion())
        verify(VerifyMode.exactly(0)) { arrangement.uploadManager.retryDraftUpload(any()) }
    }

    @Test
    fun rejectedRetryCannotCancelAnExistingTransferOrOverwriteItsStatus() = runTest {
        val drafts = CellAttachmentDraftUploadsImpl(backgroundScope).also { it.requireBackgroundOwner() }
        val (arrangement, useCase) = Arrangement(backgroundScope, drafts).arrange()
        val draft = AttachmentDraft(
            uuid = attachmentId, versionId = "version", fileName = "file", remoteFilePath = "cell/file",
            localFilePath = "/tmp/file", fileSize = 1, uploadStatus = AttachmentUploadStatus.FAILED,
            mimeType = "text/plain", assetWidth = null, assetHeight = null, assetDuration = null,
        )
        everySuspend { arrangement.repository.get(attachmentId) }.returns(draft.right())
        every { arrangement.uploadManager.retryDraftUpload(draft) }.returns(false)
        val caller = async { useCase(attachmentId) }
        runCurrent()
        val operation = drafts.uploads.value.single()
        operation.claim()
        runCurrent()
        assertTrue(caller.await().isLeft())
        assertEquals(CellAttachmentDraftUploadResult.Failed, operation.awaitCompletion())
        verifySuspend(VerifyMode.exactly(0)) { arrangement.uploadManager.cancelUpload(attachmentId) }
        verifySuspend(VerifyMode.exactly(0)) { arrangement.repository.updateStatus(any(), any()) }
    }

    private class Arrangement(
        val uploadScope: CoroutineScope,
        val draftUploads: CellAttachmentDraftUploadsImpl? = null,
    ) {

        val uploadManager = mock<CellUploadManager>(mode = MockMode.autoUnit)
        val repository = mock<MessageAttachmentDraftRepository>(mode = MockMode.autoUnit)

        val uploadEventsFlow = MutableSharedFlow<CellUploadEvent>()

        suspend fun withStatusUpdateSuccess() = apply {
            everySuspend { repository.updateStatus(any(), any()) }.returns(Unit.right())
        }

        suspend fun withStatusUpdateFailure() = apply {
            everySuspend { repository.updateStatus(any(), any()) }.returns(
                StorageFailure.DataNotFound.left()
            )
        }

        suspend fun arrange(): Pair<Arrangement, RetryAttachmentUploadUseCaseImpl> {

            everySuspend { uploadManager.retryUpload(any()) }.returns(Unit)
            every { uploadManager.observeUpload(any()) }.returns(uploadEventsFlow)

            return this to RetryAttachmentUploadUseCaseImpl(
                uploadManager = uploadManager,
                repository = repository,
                scope = uploadScope,
                draftUploads = draftUploads,
            )
        }
    }

    private fun TestScope.Arrangement() = Arrangement(this.backgroundScope)
}
