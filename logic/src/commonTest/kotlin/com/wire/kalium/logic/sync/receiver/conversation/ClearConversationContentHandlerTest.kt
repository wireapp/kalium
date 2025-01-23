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
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.IsMessageSentInSelfConversationUseCase
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.conversation.ClearConversationAssetsLocallyUseCase
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.receiver.handler.ClearConversationContentHandler
import com.wire.kalium.logic.sync.receiver.handler.ClearConversationContentHandlerImpl
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangementImpl
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
    fun givenMessageFromOtherUserAndNeedToRemove_whenMessageNotInSelfConversation_thenWholeConversationShouldNotBeDeleted() =
        runTest {
            // given
            val (arrangement, handler) = Arrangement()
                .arrange {
                    withMessageSentInSelfConversation(false)
                }

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
            coVerify { arrangement.conversationRepository.deleteConversation(any()) }.wasNotInvoked()
            coVerify { arrangement.conversationRepository.clearContent(any()) }.wasInvoked(exactly = once)
        }

    @Test
    fun givenMessageFromOtherClient_whenMessageInSelfConversation_thenDoNothing() =
        runTest {
            // given
            val (arrangement, handler) = Arrangement()
                .arrange {
                    withMessageSentInSelfConversation(true)
                }

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
            coVerify { arrangement.conversationRepository.deleteConversation(any()) }.wasNotInvoked()
            coVerify { arrangement.conversationRepository.clearContent(any()) }.wasNotInvoked()
        }

    @Test
    fun givenMessageFromOtherUser_whenMessageNotInSelfConversationAndNoNeedToRemove_thenOnlyClearContent() =
        runTest {
            // given
            val (arrangement, handler) = Arrangement()
                .arrange {
                    withMessageSentInSelfConversation(false)
                }

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
            coVerify { arrangement.conversationRepository.deleteConversation(any()) }.wasNotInvoked()
            coVerify { arrangement.conversationRepository.clearContent(any()) }.wasInvoked(exactly = once)
        }

    @Test
    fun givenMessageFromTheSelfUser_whenMessageNotInSelfConversation_thenContentNorConversationShouldBeRemoved() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .arrange {
                withMessageSentInSelfConversation(false)
            }

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
        coVerify { arrangement.conversationRepository.deleteConversation(any()) }.wasNotInvoked()
        coVerify { arrangement.conversationRepository.clearContent(any()) }.wasNotInvoked()
    }

    @Test
    fun givenSelfSenderAndMessageInSelfConversation_whenNeedToRemoveAndConversationIsNotLeftYet_thenContentCleared() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .arrange {
                withMessageSentInSelfConversation(true)
                withGetConversationMembers(listOf(TestUser.USER_ID))
            }

        // when
        handler.handle(
            message = OWN_MESSAGE,
            messageContent = MessageContent.Cleared(
                conversationId = CONVERSATION_ID,
                time = Instant.DISTANT_PAST,
                needToRemoveLocally = true
            )
        )

        // then
        coVerify { arrangement.conversationRepository.deleteConversation(any()) }.wasNotInvoked()
        coVerify { arrangement.conversationRepository.clearContent(any()) }.wasInvoked(exactly = once)
        coVerify { arrangement.conversationRepository.addConversationToDeleteQueue(any()) }.wasInvoked(exactly = once)
    }

    @Test
    fun givenSelfSenderAndMessageInSelfConversation_whenNeedToRemoveAndLeftConversation_thenContentAndConversationRemoved() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .arrange {
                withMessageSentInSelfConversation(true)
                withGetConversationMembers(listOf())
            }

        // when
        handler.handle(
            message = OWN_MESSAGE,
            messageContent = MessageContent.Cleared(
                conversationId = CONVERSATION_ID,
                time = Instant.DISTANT_PAST,
                needToRemoveLocally = true
            )
        )

        // then
        coVerify { arrangement.conversationRepository.deleteConversation(any()) }.wasInvoked(exactly = once)
        coVerify { arrangement.conversationRepository.clearContent(any()) }.wasInvoked(exactly = once)
        coVerify { arrangement.conversationRepository.addConversationToDeleteQueue(any()) }.wasNotInvoked()
    }

    @Test
    fun givenMessageFromTheSelfUser_whenMessageInSelfConversationAndNoNeedToRemove_thenOnlyContentRemoved() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .arrange {
                withMessageSentInSelfConversation(true)
            }

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
        coVerify { arrangement.conversationRepository.deleteConversation(any()) }.wasNotInvoked()
        coVerify { arrangement.conversationRepository.clearContent(any()) }.wasInvoked(exactly = once)
    }


    private class Arrangement : ConversationRepositoryArrangement by ConversationRepositoryArrangementImpl() {
        @Mock
        val isMessageSentInSelfConversationUseCase = mock(IsMessageSentInSelfConversationUseCase::class)

        @Mock
        val clearConversationAssetsLocally = mock(ClearConversationAssetsLocallyUseCase::class)

        suspend fun withMessageSentInSelfConversation(isSentInSelfConv: Boolean) = apply {
            coEvery { isMessageSentInSelfConversationUseCase(any()) }.returns(isSentInSelfConv)
        }

        suspend fun arrange(block: suspend Arrangement.() -> Unit): Pair<Arrangement, ClearConversationContentHandler> = run {
            val clearConversationContentHandler = ClearConversationContentHandlerImpl(
                conversationRepository = conversationRepository,
                selfUserId = TestUser.USER_ID,
                isMessageSentInSelfConversation = isMessageSentInSelfConversationUseCase,
                clearLocalConversationAssets = clearConversationAssetsLocally
            )
            withDeletingConversationSucceeding()
            withClearContentSucceeding()
            coEvery { clearConversationAssetsLocally(any()) }.returns(Either.Right(Unit))
            block()

            this to clearConversationContentHandler
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
