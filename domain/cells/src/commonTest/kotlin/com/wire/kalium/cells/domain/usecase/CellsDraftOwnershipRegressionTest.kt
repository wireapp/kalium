/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.cells.domain.usecase

import com.wire.kalium.cells.domain.CellAttachmentDraftUploadsImpl
import com.wire.kalium.cells.domain.CellUploadManager
import com.wire.kalium.cells.domain.CellConversationRepository
import com.wire.kalium.cells.domain.MessageAttachmentDraftRepository
import com.wire.kalium.cells.domain.CellUploadAttempt
import com.wire.kalium.cells.domain.CellUploadEvent
import com.wire.kalium.cells.domain.CellUploadInfo
import com.wire.kalium.cells.domain.model.AttachmentDraft
import com.wire.kalium.cells.domain.model.AttachmentUploadStatus
import com.wire.kalium.cells.domain.model.CellNode
import com.wire.kalium.common.functional.right
import com.wire.kalium.common.functional.isLeft
import com.wire.kalium.logic.data.id.ConversationId
import dev.mokkery.MockMode
import dev.mokkery.mock
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.matcher.any
import dev.mokkery.verifySuspend
import dev.mokkery.verify.VerifyMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CellsDraftOwnershipRegressionTest {
    @Test
    fun initialFailureKeepsNodeClaimUntilCancellationCleanupCompletes() = runTest {
        val registry = CellAttachmentDraftUploadsImpl(backgroundScope).also { it.requireBackgroundOwner() }
        val manager = mock<CellUploadManager>(mode = MockMode.autoUnit)
        val conversations = mock<CellConversationRepository>(mode = MockMode.autoUnit)
        val repository = mock<MessageAttachmentDraftRepository>(mode = MockMode.autoUnit)
        val attempt = mock<CellUploadAttempt>(mode = MockMode.autoUnit)
        val node = CellNode(uuid = "node", versionId = "version", path = "cell/file", size = 1, isDraft = true)
        everySuspend { conversations.getCellName(any()) } returns "cell".right()
        everySuspend { manager.upload(any(), any(), any()) } returns node.right()
        every { manager.captureUploadAttempt(any()) } returns attempt
        every { manager.observeUpload(any()) } returns null
        every { manager.getUploadInfo(any()) } returns null
        every { manager.consumeUploadOutcome(any()) } returns CellUploadEvent.UploadError
        everySuspend { repository.add(any(), any(), any(), any(), any(), any()) } returns Unit.right()
        val committed = CompletableDeferred<Unit>()
        val cleanupEntered = CompletableDeferred<Unit>()
        val releaseCleanup = CompletableDeferred<Unit>()
        var writes = 0
        everySuspend { repository.updateStatus(any(), any()) } calls {
            writes++
            if (writes == 1) {
                committed.complete(Unit)
                awaitCancellation() // FAILED committed, but cancellation wins before return.
            }
            Unit.right()
        }
        everySuspend { attempt.awaitStopped() } calls { cleanupEntered.complete(Unit); releaseCleanup.await() }
        val add = AddAttachmentDraftUseCaseImpl(manager, conversations, repository, backgroundScope, registry)
        val caller = async { add(ConversationId("c", "d"), "file", "text/plain", "/file".toPath(), 1, null) }
        runCurrent()
        val operation = registry.uploads.value.single()
        operation.claim()
        committed.await()
        val cancel = async { operation.cancelAndAwait() }
        cleanupEntered.await()
        val retry = RetryAttachmentUploadUseCaseImpl(manager, repository, backgroundScope, registry)
        assertTrue(retry(node.uuid).isLeft())
        releaseCleanup.complete(Unit)
        cancel.await()
        caller.await()
        assertEquals(2, writes)
        assertTrue(registry.claimNode(node.uuid))
        registry.releaseNode(node.uuid)
    }

    @Test
    fun observerDuringCancelledTransferCleanupCannotDeleteRetryableDraft() = runTest {
        val registry = CellAttachmentDraftUploadsImpl(backgroundScope).also { it.requireBackgroundOwner() }
        val manager = mock<CellUploadManager>(mode = MockMode.autoUnit)
        val repository = mock<MessageAttachmentDraftRepository>(mode = MockMode.autoUnit)
        val attempt = mock<CellUploadAttempt>(mode = MockMode.autoUnit)
        var stored = draft(AttachmentUploadStatus.FAILED)
        var uploading = true
        everySuspend { repository.get(any()) } calls { stored.right() }
        everySuspend { repository.getAll(any()) } calls { listOf(stored).right() }
        everySuspend { repository.observe(any()) } returns flowOf(emptyList())
        everySuspend { repository.updateStatus(any(), any()) } calls {
            stored = stored.copy(uploadStatus = it.args[1] as AttachmentUploadStatus)
            Unit.right()
        }
        every { manager.retryDraftUpload(any()) } returns true
        every { manager.captureUploadAttempt(any()) } returns attempt
        every { manager.observeUpload(any()) } returns kotlinx.coroutines.flow.MutableSharedFlow<CellUploadEvent>()
        every { manager.getUploadInfo(any()) } returns CellUploadInfo()
        every { manager.consumeUploadOutcome(any()) } returns null
        every { manager.isUploading(any()) } calls { uploading }
        val removed = CompletableDeferred<Unit>()
        val finishCleanup = CompletableDeferred<Unit>()
        everySuspend { attempt.cancelAndAwait() } calls {
            uploading = false
            removed.complete(Unit)
            finishCleanup.await()
        }
        val retry = RetryAttachmentUploadUseCaseImpl(manager, repository, backgroundScope, registry)
        val caller = async { retry(stored.uuid) }
        runCurrent()
        val operation = registry.uploads.value.single()
        operation.claim()
        runCurrent()
        caller.await()
        val cancellation = async { operation.cancelAndAwait() }
        removed.await()
        assertEquals(AttachmentUploadStatus.UPLOADING, stored.uploadStatus)
        val observer = ObserveAttachmentDraftsUseCaseImpl(repository, manager, draftUploads = registry)
        observer(ConversationId("c", "d"))
        verifySuspend(VerifyMode.exactly(0)) { repository.remove(any()) }
        finishCleanup.complete(Unit)
        cancellation.await()
        assertEquals(AttachmentUploadStatus.FAILED, stored.uploadStatus)
        // An observer may still hold a stale UPLOADING list after the claim has been released.
        everySuspend { repository.getAll(any()) } returns listOf(draft(AttachmentUploadStatus.UPLOADING)).right()
        observer(ConversationId("c", "d"))
        verifySuspend(VerifyMode.exactly(0)) { repository.remove(any()) }
    }

    private fun draft(status: AttachmentUploadStatus) = AttachmentDraft(
        uuid = "node", versionId = "version", fileName = "file", remoteFilePath = "cell/file",
        localFilePath = "/file", fileSize = 1, uploadStatus = status,
        mimeType = "text/plain", assetWidth = null, assetHeight = null, assetDuration = null,
    )
}
