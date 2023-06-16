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

import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.feature.CurrentClientIdProvider
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.logic.util.shouldSucceed
import io.mockative.Mock
import io.mockative.anything
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SendConfirmationUseCaseTest {

    @Test
    fun givenAConversationId_whenReadConfirmationsEnabled_thenShouldSendConfirmation() = runTest {
        val (arrangement, sendConfirmation) = Arrangement()
            .withCurrentClientIdProvider()
            .withGetConversationByIdSuccessful()
            .withToggleReadReceiptsStatus(true)
            .withPendingMessagesResponse()
            .withSendMessageSuccess()
            .arrange()

        val result = sendConfirmation(TestConversation.ID)

        result.shouldSucceed()
        verify(arrangement.messageSender)
            .suspendFunction(arrangement.messageSender::sendMessage)
            .with(anything(), anything())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAConversationId_whenReadConfirmationsDisabled_thenShouldNOTSendConfirmation() = runTest {
        val (arrangement, sendConfirmation) = Arrangement()
            .withCurrentClientIdProvider()
            .withGetConversationByIdSuccessful()
            .withToggleReadReceiptsStatus(false)
            .withPendingMessagesResponse()
            .withSendMessageSuccess()
            .arrange()

        val result = sendConfirmation(TestConversation.ID)

        result.shouldSucceed()
        verify(arrangement.messageSender)
            .suspendFunction(arrangement.messageSender::sendMessage)
            .with(anything(), anything())
            .wasNotInvoked()

        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::getPendingConfirmationMessagesByConversationAfterDate)
            .with(anything(), anything())
            .wasNotInvoked()
    }

    private class Arrangement {

        @Mock
        private val currentClientIdProvider = mock(classOf<CurrentClientIdProvider>())

        @Mock
        private val syncManager = mock(classOf<SyncManager>())

        @Mock
        val messageSender = mock(classOf<MessageSender>())

        @Mock
        private val conversationRepository = mock(classOf<ConversationRepository>())

        @Mock
        val messageRepository = mock(classOf<MessageRepository>())

        @Mock
        private val userPropertyRepository = mock(classOf<UserPropertyRepository>())

        fun withCurrentClientIdProvider() = apply {
            given(currentClientIdProvider)
                .suspendFunction(currentClientIdProvider::invoke)
                .whenInvoked()
                .thenReturn(Either.Right(TestClient.CLIENT_ID))
        }

        fun withSendMessageSuccess() = apply {
            given(messageSender)
                .suspendFunction(messageSender::sendMessage)
                .whenInvokedWith(anything(), anything())
                .thenReturn(Either.Right(Unit))
        }

        fun withGetConversationByIdSuccessful() = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::baseInfoById)
                .whenInvokedWith(anything())
                .thenReturn(Either.Right(TestConversation.CONVERSATION))
        }

        fun withToggleReadReceiptsStatus(enabled: Boolean = false) = apply {
            given(userPropertyRepository)
                .suspendFunction(userPropertyRepository::getReadReceiptsStatus)
                .whenInvoked()
                .thenReturn(enabled)
        }

        fun withPendingMessagesResponse() = apply {
            given(messageRepository)
                .suspendFunction(messageRepository::getPendingConfirmationMessagesByConversationAfterDate)
                .whenInvokedWith(anything(), anything())
                .thenReturn(Either.Right(listOf(TestMessage.TEXT_MESSAGE.id)))
        }

        fun arrange() = this to SendConfirmationUseCase(
            currentClientIdProvider,
            syncManager,
            messageSender,
            TestUser.SELF.id,
            conversationRepository,
            messageRepository,
            userPropertyRepository
        )
    }

}
