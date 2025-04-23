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

package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.util.KaliumDispatcher
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class SendEditTextMessageUseCaseTest {

    @Test
    fun givenAValidMessage_whenSendingEditTextIsSuccessful_thenMarkMessageAsSentAndReturnSuccess() = runTest {
        // Given
        val (arrangement, sendEditTextMessage) = Arrangement(testKaliumDispatcher)
            .withSlowSyncStatusComplete()
            .withCurrentClientProviderSuccess()
            .withUpdateTextMessageSuccess()
            .withUpdateMessageStatusSuccess()
            .withSendMessageSuccess()
            .arrange()
        val originalMessageId = "message id"
        val editedMessageId = "edited message id"
        val editedMessageText = "text"

        // When
        val result = sendEditTextMessage(TestConversation.ID, originalMessageId, editedMessageText, listOf(), editedMessageId)

        // Then
        result.shouldSucceed()
        coVerify {
            arrangement.messageRepository.updateTextMessage(any(), any(), eq(originalMessageId), any())
        }.wasInvoked(once)
        coVerify {
            arrangement.messageRepository.updateMessageStatus(eq(MessageEntity.Status.PENDING), any(), any())
        }.wasInvoked(once)
        coVerify {
            arrangement.messageSendFailureHandler.handleFailureAndUpdateMessageStatus(any(), any(), any(), any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.messageSender.sendMessage(any(), any())
        }.wasInvoked(once)
    }

    @Test
    fun givenAValidMessage_whenSendingEditTextIsFailed_thenMarkMessageAsFailedAndReturnFailure() = runTest {
        // Given
        val (arrangement, sendEditTextMessage) = Arrangement(testKaliumDispatcher)
            .withSlowSyncStatusComplete()
            .withCurrentClientProviderSuccess()
            .withUpdateTextMessageSuccess()
            .withUpdateMessageStatusSuccess()
            .withSendMessageFailure()
            .arrange()
        val originalMessageId = "message id"
        val editedMessageId = "edited message id"
        val editedMessageText = "text"

        // When
        val result = sendEditTextMessage(TestConversation.ID, originalMessageId, editedMessageText, listOf(), editedMessageId)

        // Then
        result.shouldFail()
        coVerify {
            arrangement.messageRepository.updateTextMessage(any(), any(), eq(originalMessageId), any())
        }.wasInvoked(once)
        coVerify {
            arrangement.messageRepository.updateTextMessage(any(), any(), eq(editedMessageId), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.messageRepository.updateMessageStatus(eq(MessageEntity.Status.PENDING), any(), any())
        }.wasInvoked(once)
        coVerify {
            arrangement.messageSendFailureHandler.handleFailureAndUpdateMessageStatus(any(), any(), any(), any(), any())
        }.wasInvoked(once)
        coVerify {
            arrangement.messageSender.sendMessage(any(), any())
        }.wasInvoked(once)
    }

    private class Arrangement(var dispatcher: KaliumDispatcher = TestKaliumDispatcher) {
        val messageRepository = mock(MessageRepository::class)
        val currentClientIdProvider = mock(CurrentClientIdProvider::class)
        val slowSyncRepository = mock(SlowSyncRepository::class)
        val messageSender = mock(MessageSender::class)
        val messageSendFailureHandler = mock(MessageSendFailureHandler::class)

        suspend fun withSendMessageSuccess() = apply {
            coEvery {
                messageSender.sendMessage(any(), any())
            }.returns(Either.Right(Unit))
        }
        suspend fun withSendMessageFailure() = apply {
            coEvery {
                messageSender.sendMessage(any(), any())
            }.returns(Either.Left(NetworkFailure.NoNetworkConnection(null)))
        }
        fun withSlowSyncStatusComplete() = apply {
            val stateFlow = MutableStateFlow<SlowSyncStatus>(SlowSyncStatus.Complete).asStateFlow()
            every {
                slowSyncRepository.slowSyncStatus
            }.returns(stateFlow)
        }
        suspend fun withCurrentClientProviderSuccess(clientId: ClientId = TestClient.CLIENT_ID) = apply {
            coEvery {
                currentClientIdProvider.invoke()
            }.returns(Either.Right(clientId))
        }
        suspend fun withUpdateTextMessageSuccess() = apply {
            coEvery {
                messageRepository.updateTextMessage(any(), any(), any(), any())
            }.returns(Either.Right(Unit))
        }
        suspend fun withUpdateMessageStatusSuccess() = apply {
            coEvery {
                messageRepository.updateMessageStatus(any(), any(), any())
            }.returns(Either.Right(Unit))
        }

        fun arrange() = this to SendEditTextMessageUseCase(
            messageRepository,
            TestUser.SELF.id,
            currentClientIdProvider,
            slowSyncRepository,
            messageSender,
            messageSendFailureHandler,
            dispatcher
        )
    }
}
