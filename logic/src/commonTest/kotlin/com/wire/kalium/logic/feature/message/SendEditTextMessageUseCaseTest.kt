/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.feature.CurrentClientIdProvider
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.persistence.dao.message.MessageEntity
import io.mockative.Mock
import io.mockative.any
import io.mockative.anything
import io.mockative.classOf
import io.mockative.configure
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SendEditTextMessageUseCaseTest {

    @Test
    fun givenAValidMessage_whenSendingEditTextIsSuccessful_thenMarkMessageAsSentAndReturnSuccess() = runTest {
        // Given
        val (arrangement, sendEditTextMessage) = Arrangement()
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
        assertTrue(result is Either.Right)
        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::updateTextMessage)
            .with(any(), any(), eq(originalMessageId), any())
            .wasInvoked(once)
        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::updateMessageStatus)
            .with(eq(MessageEntity.Status.PENDING), any(), any())
            .wasInvoked(once)
        verify(arrangement.messageSendFailureHandler)
            .suspendFunction(arrangement.messageSendFailureHandler::handleFailureAndUpdateMessageStatus)
            .with(any(), any(), any(), any(), any())
            .wasNotInvoked()
        verify(arrangement.messageSender)
            .suspendFunction(arrangement.messageSender::sendMessage)
            .with(any(), any())
            .wasInvoked(once)
    }

    @Test
    fun givenAValidMessage_whenSendingEditTextIsFailed_thenMarkMessageAsFailedAndReturnFailure() = runTest {
        // Given
        val (arrangement, sendEditTextMessage) = Arrangement()
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
        assertTrue(result is Either.Left)
        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::updateTextMessage)
            .with(any(), any(), eq(originalMessageId), any())
            .wasInvoked(once)
        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::updateTextMessage)
            .with(any(), any(), eq(editedMessageId), any())
            .wasNotInvoked()
        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::updateMessageStatus)
            .with(eq(MessageEntity.Status.PENDING), any(), any())
            .wasInvoked(once)
        verify(arrangement.messageSendFailureHandler)
            .suspendFunction(arrangement.messageSendFailureHandler::handleFailureAndUpdateMessageStatus)
            .with(any(), any(), any(), any(), any())
            .wasInvoked(once)
        verify(arrangement.messageSender)
            .suspendFunction(arrangement.messageSender::sendMessage)
            .with(any(), any())
            .wasInvoked(once)
    }

    private class Arrangement {

        @Mock
        val messageRepository = mock(classOf<MessageRepository>())
        @Mock
        val currentClientIdProvider = mock(classOf<CurrentClientIdProvider>())
        @Mock
        val slowSyncRepository = mock(classOf<SlowSyncRepository>())
        @Mock
        val messageSender = mock(classOf<MessageSender>())
        @Mock
        val messageSendFailureHandler = configure(mock(classOf<MessageSendFailureHandler>())) { stubsUnitByDefault = true }

        fun withSendMessageSuccess() = apply {
            given(messageSender)
                .suspendFunction(messageSender::sendMessage)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Right(Unit))
        }
        fun withSendMessageFailure() = apply {
            given(messageSender)
                .suspendFunction(messageSender::sendMessage)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Left(NetworkFailure.NoNetworkConnection(null)))
        }
        fun withSlowSyncStatusComplete() = apply {
            val stateFlow = MutableStateFlow<SlowSyncStatus>(SlowSyncStatus.Complete).asStateFlow()
            given(slowSyncRepository)
                .getter(slowSyncRepository::slowSyncStatus)
                .whenInvoked()
                .thenReturn(stateFlow)
        }
        fun withCurrentClientProviderSuccess(clientId: ClientId = TestClient.CLIENT_ID) = apply {
            given(currentClientIdProvider)
                .suspendFunction(currentClientIdProvider::invoke)
                .whenInvoked()
                .thenReturn(Either.Right(clientId))
        }
        fun withUpdateTextMessageSuccess() = apply {
            given(messageRepository)
                .suspendFunction(messageRepository::updateTextMessage)
                .whenInvokedWith(anything(), anything(), anything(), anything())
                .thenReturn(Either.Right(Unit))
        }
        fun withUpdateMessageStatusSuccess() = apply {
            given(messageRepository)
                .suspendFunction(messageRepository::updateMessageStatus)
                .whenInvokedWith(anything(), anything(), anything())
                .thenReturn(Either.Right(Unit))
        }

        fun arrange() = this to SendEditTextMessageUseCase(
            messageRepository,
            TestUser.SELF.id,
            currentClientIdProvider,
            slowSyncRepository,
            messageSender,
            messageSendFailureHandler
        )
    }
}
