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

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
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
        val (arrangement, useCase) = Arrangement().withSuccessfulResponse().arrange()

        // When
        val result = useCase.invoke(newDownloadStatus, dummyConvId, dummyMessageId)

        // Then
        assertTrue(result is UpdateTransferStatusResult.Success)
        coVerify {
            arrangement.messageRepository.updateAssetMessageTransferStatus(eq(newDownloadStatus), eq(dummyConvId), eq(dummyMessageId))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenAnErrorDownloadStatusUpdateRequest_whenInvoked_thenCoreFailureIsReturned() = runTest {
        // Given
        val newDownloadStatus = AssetTransferStatus.SAVED_INTERNALLY
        val dummyConvId = ConversationId("dummy-value", "dummy.domain")
        val dummyMessageId = "dummy-message-id"
        val (arrangement, useCase) = Arrangement().withErrorResponse().arrange()

        // When
        val result = useCase.invoke(newDownloadStatus, dummyConvId, dummyMessageId)

        // Then
        assertTrue(result is UpdateTransferStatusResult.Failure)
        coVerify {
            arrangement.messageRepository.updateAssetMessageTransferStatus(eq(newDownloadStatus), eq(dummyConvId), eq(dummyMessageId))
        }.wasInvoked(exactly = once)
    }

    private class Arrangement {
        @Mock
        val messageRepository = mock(classOf<MessageRepository>())

        suspend fun withSuccessfulResponse(): Arrangement {
            coEvery {
                messageRepository.updateAssetMessageTransferStatus(any(), any(), any())
            }.returns(Either.Right(Unit))
            return this
        }

        suspend fun withErrorResponse(): Arrangement {
            coEvery {
                messageRepository.updateAssetMessageTransferStatus(any(), any(), any())
            }.returns(Either.Left(NetworkFailure.ServerMiscommunication(RuntimeException())))
            return this
        }

        fun arrange() = this to UpdateAssetMessageTransferStatusUseCaseImpl(messageRepository)

    }
}
