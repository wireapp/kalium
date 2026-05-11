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
import com.wire.kalium.cells.domain.CellUploadEvent
import com.wire.kalium.cells.domain.CellUploadManager
import com.wire.kalium.cells.domain.MessageAttachmentDraftRepository
import com.wire.kalium.cells.domain.model.AttachmentUploadStatus
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.isLeft
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import dev.mokkery.matcher.any
import dev.mokkery.everySuspend
import dev.mokkery.verifySuspend
import dev.mokkery.verify.VerifyMode
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

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

    private class Arrangement(val uploadScope: CoroutineScope) {

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
            )
        }
    }

    private fun TestScope.Arrangement() = Arrangement(this.backgroundScope)
}
