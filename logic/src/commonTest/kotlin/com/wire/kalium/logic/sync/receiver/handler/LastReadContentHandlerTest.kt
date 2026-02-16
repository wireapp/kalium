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

package com.wire.kalium.logic.sync.receiver.handler

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.IsMessageSentInSelfConversationUseCase
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.notification.NotificationEventsManager
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test

class LastReadContentHandlerTest {

    @Test
    fun givenSameConversationAndOlderLastRead_whenFlushing_thenShouldUseOnlyNewestTimestamp() = runTest {
        val newer = Instant.parse("2026-02-10T12:00:00Z")
        val older = Instant.parse("2026-02-10T11:59:59Z")
        val (arrangement, handler) = Arrangement()
            .withIsMessageSentInSelfConversation(true)
            .withUpdateReadDateAndGetHasUnreadEvents(Either.Right(false))
            .arrange()

        handler.handle(Arrangement.selfSignalingMessage(), Arrangement.lastReadContent(CONVERSATION_ID, newer))
        handler.handle(Arrangement.selfSignalingMessage(), Arrangement.lastReadContent(CONVERSATION_ID, older))
        handler.flushPendingLastReads()

        coVerify {
            arrangement.conversationRepository.updateReadDateAndGetHasUnreadEvents(eq(CONVERSATION_ID), eq(newer))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenSameConversationAndSameTimestamp_whenFlushing_thenShouldUpdateOnce() = runTest {
        val timestamp = Instant.parse("2026-02-10T12:00:00Z")
        val (arrangement, handler) = Arrangement()
            .withIsMessageSentInSelfConversation(true)
            .withUpdateReadDateAndGetHasUnreadEvents(Either.Right(false))
            .arrange()

        handler.handle(Arrangement.selfSignalingMessage(), Arrangement.lastReadContent(CONVERSATION_ID, timestamp))
        handler.handle(Arrangement.selfSignalingMessage(), Arrangement.lastReadContent(CONVERSATION_ID, timestamp))
        handler.flushPendingLastReads()

        coVerify {
            arrangement.conversationRepository.updateReadDateAndGetHasUnreadEvents(eq(CONVERSATION_ID), eq(timestamp))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenDifferentConversation_whenFlushing_thenShouldUpdateBoth() = runTest {
        val timestamp = Instant.parse("2026-02-10T12:00:00Z")
        val (arrangement, handler) = Arrangement()
            .withIsMessageSentInSelfConversation(true)
            .withUpdateReadDateAndGetHasUnreadEvents(Either.Right(true))
            .arrange()

        handler.handle(Arrangement.selfSignalingMessage(), Arrangement.lastReadContent(CONVERSATION_ID, timestamp))
        handler.handle(Arrangement.selfSignalingMessage(), Arrangement.lastReadContent(OTHER_CONVERSATION_ID, timestamp))
        handler.flushPendingLastReads()

        coVerify {
            arrangement.conversationRepository.updateReadDateAndGetHasUnreadEvents(eq(CONVERSATION_ID), eq(timestamp))
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.conversationRepository.updateReadDateAndGetHasUnreadEvents(eq(OTHER_CONVERSATION_ID), eq(timestamp))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenMessageIsNotFromSelfUser_whenFlushing_thenShouldDoNothing() = runTest {
        val timestamp = Instant.parse("2026-02-10T12:00:00Z")
        val (arrangement, handler) = Arrangement()
            .withIsMessageSentInSelfConversation(true)
            .arrange()

        handler.handle(Arrangement.otherUserSignalingMessage(), Arrangement.lastReadContent(CONVERSATION_ID, timestamp))
        handler.flushPendingLastReads()

        coVerify {
            arrangement.conversationRepository.updateReadDateAndGetHasUnreadEvents(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.notificationEventsManager.scheduleConversationSeenNotification(any())
        }.wasNotInvoked()
    }

    @Test
    fun givenFlushCalledTwice_whenNoNewLastRead_thenShouldNotUpdateAgain() = runTest {
        val timestamp = Instant.parse("2026-02-10T12:00:00Z")
        val (arrangement, handler) = Arrangement()
            .withIsMessageSentInSelfConversation(true)
            .withUpdateReadDateAndGetHasUnreadEvents(Either.Right(true))
            .arrange()

        handler.handle(Arrangement.selfSignalingMessage(), Arrangement.lastReadContent(CONVERSATION_ID, timestamp))
        handler.flushPendingLastReads()
        handler.flushPendingLastReads()

        coVerify {
            arrangement.conversationRepository.updateReadDateAndGetHasUnreadEvents(eq(CONVERSATION_ID), eq(timestamp))
        }.wasInvoked(exactly = once)
    }

    private class Arrangement {
        val conversationRepository = mock(ConversationRepository::class)
        private val isMessageSentInSelfConversation = mock(IsMessageSentInSelfConversationUseCase::class)
        val notificationEventsManager = mock(NotificationEventsManager::class)

        suspend fun withIsMessageSentInSelfConversation(isSelfConversation: Boolean) = apply {
            coEvery { isMessageSentInSelfConversation.invoke(any()) }.returns(isSelfConversation)
        }

        suspend fun withUpdateReadDateAndGetHasUnreadEvents(result: Either<StorageFailure, Boolean>) = apply {
            coEvery {
                conversationRepository.updateReadDateAndGetHasUnreadEvents(any(), any())
            }.returns(result)
        }

        fun arrange() = this to LastReadContentHandlerImpl(
            conversationRepository = conversationRepository,
            selfUserId = SELF_USER_ID,
            isMessageSentInSelfConversation = isMessageSentInSelfConversation,
            notificationEventsManager = notificationEventsManager
        )

        companion object {
            fun selfSignalingMessage() = Message.Signaling(
                id = "signaling-id",
                content = MessageContent.LastRead(
                    messageId = "seed-message-id",
                    conversationId = CONVERSATION_ID,
                    time = Instant.parse("2026-02-10T12:00:00Z")
                ),
                conversationId = CONVERSATION_ID,
                date = Instant.parse("2026-02-10T12:00:00Z"),
                senderUserId = SELF_USER_ID,
                senderClientId = ClientId("self-client"),
                status = Message.Status.Sent,
                isSelfMessage = true,
                expirationData = null
            )

            fun otherUserSignalingMessage() = selfSignalingMessage().copy(
                senderUserId = OTHER_USER_ID,
                isSelfMessage = false
            )

            fun lastReadContent(conversationId: ConversationId, timestamp: Instant) = MessageContent.LastRead(
                messageId = "message-id",
                conversationId = conversationId,
                time = timestamp
            )
        }
    }

    private companion object {
        val SELF_USER_ID = TestUser.SELF.id
        val OTHER_USER_ID = TestUser.OTHER_USER_ID
        val CONVERSATION_ID = TestConversation.CONVERSATION.id
        val OTHER_CONVERSATION_ID = ConversationId("other-conversation", "wire.com")
    }
}
