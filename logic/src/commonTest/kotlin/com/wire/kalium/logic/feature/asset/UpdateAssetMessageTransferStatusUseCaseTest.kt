/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

package com.wire.kalium.logic.feature.asset

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import com.wire.kalium.util.KaliumDispatcher
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class UpdateAssetMessageTransferStatusUseCaseTest {

    @Test
    fun givenAValidDownloadStatusUpdateRequest_whenInvoked_thenResultSuccessIsReturned() = runTest {
        // Given
        val newDownloadStatus = AssetTransferStatus.DOWNLOAD_IN_PROGRESS
        val dummyConvId = ConversationId("dummy-value", "dummy.domain")
        val dummyMessageId = "dummy-message-id"
        val (arrangement, useCase) = Arrangement(testKaliumDispatcher)
            .withSuccessfulResponse()
            .arrange()

        // When
        val result = useCase.invoke(newDownloadStatus, dummyConvId, dummyMessageId)

        // Then
        assertTrue(result is UpdateTransferStatusResult.Success)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageRepository.updateAssetMessageTransferStatus(
                newDownloadStatus,
                dummyConvId,
                dummyMessageId
            )
        }
    }

    @Test
    fun givenAnErrorDownloadStatusUpdateRequest_whenInvoked_thenCoreFailureIsReturned() = runTest {
        // Given
        val newDownloadStatus = AssetTransferStatus.SAVED_INTERNALLY
        val dummyConvId = ConversationId("dummy-value", "dummy.domain")
        val dummyMessageId = "dummy-message-id"
        val (arrangement, useCase) = Arrangement(testKaliumDispatcher)
            .withErrorResponse()
            .arrange()

        // When
        val result = useCase.invoke(newDownloadStatus, dummyConvId, dummyMessageId)

        // Then
        assertTrue(result is UpdateTransferStatusResult.Failure)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageRepository.updateAssetMessageTransferStatus(
                newDownloadStatus,
                dummyConvId,
                dummyMessageId
            )
        }
    }

    private class Arrangement(var dispatcher: KaliumDispatcher = TestKaliumDispatcher) {
        val messageRepository = mock<MessageRepository>(mode = MockMode.autoUnit)

        suspend fun withSuccessfulResponse(): Arrangement {
            everySuspend {
                messageRepository.updateAssetMessageTransferStatus(any(), any(), any())
            } returns Either.Right(Unit)
            return this
        }

        suspend fun withErrorResponse(): Arrangement {
            everySuspend {
                messageRepository.updateAssetMessageTransferStatus(any(), any(), any())
            } returns Either.Left(NetworkFailure.ServerMiscommunication(RuntimeException()))
            return this
        }

        fun arrange() = this to UpdateAssetMessageTransferStatusUseCaseImpl(messageRepository, dispatcher)

    }
}
