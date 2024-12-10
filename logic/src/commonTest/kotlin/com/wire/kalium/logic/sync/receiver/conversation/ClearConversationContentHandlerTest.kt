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
package com.wire.kalium.logic.sync.receiver.conversation

import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.IsMessageSentInSelfConversationUseCase
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.receiver.handler.ClearConversationContentHandler
import com.wire.kalium.logic.sync.receiver.handler.ClearConversationContentHandlerImpl
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test

class ClearConversationContentHandlerTest {

    @Test
    fun givenMessageFromOtherClient_whenMessageNeedsToBeRemovedLocallyAndUserIsNotPartOfConversation_thenWholeConversationShouldBeDeleted() =
        runTest {
            // given
            val (arrangement, handler) = Arrangement()
                .withMessageSentInSelfConversation(false)
                .arrange()

            // when
            handler.handle(
                message = MESSAGE,
                messageContent = MessageContent.Cleared(
                    conversationId = CONVERSATION_ID,
                    time = Instant.DISTANT_PAST,
                    needToRemoveLocally = true
                )
            )

            // then
            coVerify { arrangement.conversationRepository.deleteConversation(any()) }
                .wasInvoked(exactly = once)
            coVerify { arrangement.conversationRepository.clearContent(any()) }
                .wasNotInvoked()
        }

    @Test
    fun givenMessageFromOtherClient_whenMessageNeedsToBeRemovedLocallyAndUserIsPartOfConversation_thenOnlyContentShouldBeCleared() =
        runTest {
            // given
            val (arrangement, handler) = Arrangement()
                .withMessageSentInSelfConversation(true)
                .arrange()

            // when
            handler.handle(
                message = MESSAGE,
                messageContent = MessageContent.Cleared(
                    conversationId = CONVERSATION_ID,
                    time = Instant.DISTANT_PAST,
                    needToRemoveLocally = true
                )
            )

            // then
            coVerify { arrangement.conversationRepository.deleteConversation(any()) }
                .wasNotInvoked()
            coVerify { arrangement.conversationRepository.clearContent(any()) }
                .wasInvoked(exactly = once)
        }

    @Test
    fun givenMessageFromOtherClient_whenMessageDoesNotNeedToBeRemovedAndUserIsNotPartOfConversation_thenContentNorConversationShouldBeRemoved() =
        runTest {
            // given
            val (arrangement, handler) = Arrangement()
                .withMessageSentInSelfConversation(false)
                .arrange()

            // when
            handler.handle(
                message = MESSAGE,
                messageContent = MessageContent.Cleared(
                    conversationId = CONVERSATION_ID,
                    time = Instant.DISTANT_PAST,
                    needToRemoveLocally = false
                )
            )

            // then
            coVerify { arrangement.conversationRepository.deleteConversation(any()) }
                .wasNotInvoked()
            coVerify { arrangement.conversationRepository.clearContent(any()) }
                .wasNotInvoked()
        }

    @Test
    fun givenMessageFromOtherClient_whenMessageDoesNotNeedToBeRemovedAndUserIsPartOfConversation_thenContentShouldBeRemoved() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .withMessageSentInSelfConversation(true)
            .arrange()

        // when
        handler.handle(
            message = MESSAGE,
            messageContent = MessageContent.Cleared(
                conversationId = CONVERSATION_ID,
                time = Instant.DISTANT_PAST,
                needToRemoveLocally = false
            )
        )

        // then
        coVerify { arrangement.conversationRepository.deleteConversation(any()) }
            .wasNotInvoked()
        coVerify { arrangement.conversationRepository.clearContent(any()) }
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenMessageFromTheSameClient_whenHandleIsInvoked_thenContentNorConversationShouldBeRemoved() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .withMessageSentInSelfConversation(true)
            .arrange()

        // when
        handler.handle(
            message = OWN_MESSAGE,
            messageContent = MessageContent.Cleared(
                conversationId = CONVERSATION_ID,
                time = Instant.DISTANT_PAST,
                needToRemoveLocally = false
            )
        )

        // then
        coVerify { arrangement.conversationRepository.deleteConversation(any()) }
            .wasNotInvoked()
        coVerify { arrangement.conversationRepository.clearContent(any()) }
            .wasNotInvoked()
    }


    private class Arrangement {
        @Mock
        val conversationRepository = mock(ConversationRepository::class)

        @Mock
        val isMessageSentInSelfConversationUseCase = mock(IsMessageSentInSelfConversationUseCase::class)

        suspend fun withMessageSentInSelfConversation(isSentInSelfConv: Boolean) = apply {
            coEvery { isMessageSentInSelfConversationUseCase(any()) }.returns(isSentInSelfConv)
        }

        suspend fun arrange(): Pair<Arrangement, ClearConversationContentHandler> =
            this to ClearConversationContentHandlerImpl(
                conversationRepository = conversationRepository,
                selfUserId = TestUser.USER_ID,
                isMessageSentInSelfConversation = isMessageSentInSelfConversationUseCase,
            ).apply {
                coEvery { conversationRepository.deleteConversation(any()) }.returns(Either.Right(Unit))
                coEvery { conversationRepository.clearContent(any()) }.returns(Either.Right(Unit))
            }
    }

    companion object {
        private val CONVERSATION_ID = ConversationId("conversationId", "domain")
        private val OTHER_USER_ID = UserId("otherUserId", "domain")

        private val MESSAGE_CONTENT = MessageContent.DataTransfer(
            trackingIdentifier = MessageContent.DataTransfer.TrackingIdentifier(
                identifier = "abcd-1234-efgh-5678"
            )
        )
        val MESSAGE = Message.Signaling(
            id = "messageId",
            content = MESSAGE_CONTENT,
            conversationId = CONVERSATION_ID,
            date = Instant.DISTANT_PAST,
            senderUserId = OTHER_USER_ID,
            senderClientId = ClientId("deviceId"),
            status = Message.Status.Sent,
            isSelfMessage = false,
            expirationData = null,
        )

        val OWN_MESSAGE = MESSAGE.copy(senderUserId = TestUser.USER_ID)
    }
}
