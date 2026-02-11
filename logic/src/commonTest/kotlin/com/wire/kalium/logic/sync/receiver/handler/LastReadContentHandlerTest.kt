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

    private val conversationRepository = mock(ConversationRepository::class)
    private val isMessageSentInSelfConversation = mock(IsMessageSentInSelfConversationUseCase::class)
    private val notificationEventsManager = mock(NotificationEventsManager::class)

    private val handler = LastReadContentHandlerImpl(
        conversationRepository = conversationRepository,
        selfUserId = SELF_USER_ID,
        isMessageSentInSelfConversation = isMessageSentInSelfConversation,
        notificationEventsManager = notificationEventsManager
    )

    @Test
    fun givenSameConversationAndOlderLastRead_whenFlushing_thenShouldUseOnlyNewestTimestamp() = runTest {
        val newer = Instant.parse("2026-02-10T12:00:00Z")
        val older = Instant.parse("2026-02-10T11:59:59Z")

        coEvery { isMessageSentInSelfConversation.invoke(any()) }.returns(true)
        coEvery { conversationRepository.updateReadDateAndGetHasUnreadEvents(any(), any()) }.returns(Either.Right(false))

        handler.handle(selfSignalingMessage(), lastReadContent(CONVERSATION_ID, newer))
        handler.handle(selfSignalingMessage(), lastReadContent(CONVERSATION_ID, older))
        handler.flushPendingLastReads()

        coVerify {
            conversationRepository.updateReadDateAndGetHasUnreadEvents(eq(CONVERSATION_ID), eq(newer))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenSameConversationAndSameTimestamp_whenFlushing_thenShouldUpdateOnce() = runTest {
        val timestamp = Instant.parse("2026-02-10T12:00:00Z")

        coEvery { isMessageSentInSelfConversation.invoke(any()) }.returns(true)
        coEvery { conversationRepository.updateReadDateAndGetHasUnreadEvents(any(), any()) }.returns(Either.Right(false))

        handler.handle(selfSignalingMessage(), lastReadContent(CONVERSATION_ID, timestamp))
        handler.handle(selfSignalingMessage(), lastReadContent(CONVERSATION_ID, timestamp))
        handler.flushPendingLastReads()

        coVerify {
            conversationRepository.updateReadDateAndGetHasUnreadEvents(eq(CONVERSATION_ID), eq(timestamp))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenDifferentConversation_whenFlushing_thenShouldUpdateBoth() = runTest {
        val timestamp = Instant.parse("2026-02-10T12:00:00Z")

        coEvery { isMessageSentInSelfConversation.invoke(any()) }.returns(true)
        coEvery { conversationRepository.updateReadDateAndGetHasUnreadEvents(any(), any()) }.returns(Either.Right(true))

        handler.handle(selfSignalingMessage(), lastReadContent(CONVERSATION_ID, timestamp))
        handler.handle(selfSignalingMessage(), lastReadContent(OTHER_CONVERSATION_ID, timestamp))
        handler.flushPendingLastReads()

        coVerify {
            conversationRepository.updateReadDateAndGetHasUnreadEvents(eq(CONVERSATION_ID), eq(timestamp))
        }.wasInvoked(exactly = once)
        coVerify {
            conversationRepository.updateReadDateAndGetHasUnreadEvents(eq(OTHER_CONVERSATION_ID), eq(timestamp))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenMessageIsNotFromSelfUser_whenFlushing_thenShouldDoNothing() = runTest {
        val timestamp = Instant.parse("2026-02-10T12:00:00Z")

        coEvery { isMessageSentInSelfConversation.invoke(any()) }.returns(true)

        handler.handle(otherUserSignalingMessage(), lastReadContent(CONVERSATION_ID, timestamp))
        handler.flushPendingLastReads()

        coVerify {
            conversationRepository.updateReadDateAndGetHasUnreadEvents(any(), any())
        }.wasNotInvoked()
        coVerify {
            notificationEventsManager.scheduleConversationSeenNotification(any())
        }.wasNotInvoked()
    }

    @Test
    fun givenFlushCalledTwice_whenNoNewLastRead_thenShouldNotUpdateAgain() = runTest {
        val timestamp = Instant.parse("2026-02-10T12:00:00Z")

        coEvery { isMessageSentInSelfConversation.invoke(any()) }.returns(true)
        coEvery { conversationRepository.updateReadDateAndGetHasUnreadEvents(any(), any()) }.returns(Either.Right(true))

        handler.handle(selfSignalingMessage(), lastReadContent(CONVERSATION_ID, timestamp))
        handler.flushPendingLastReads()
        handler.flushPendingLastReads()

        coVerify {
            conversationRepository.updateReadDateAndGetHasUnreadEvents(eq(CONVERSATION_ID), eq(timestamp))
        }.wasInvoked(exactly = once)
    }

    private fun selfSignalingMessage() = Message.Signaling(
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

    private fun otherUserSignalingMessage() = selfSignalingMessage().copy(
        senderUserId = OTHER_USER_ID,
        isSelfMessage = false
    )

    private fun lastReadContent(conversationId: ConversationId, timestamp: Instant) = MessageContent.LastRead(
        messageId = "message-id",
        conversationId = conversationId,
        time = timestamp
    )

    private companion object {
        val SELF_USER_ID = TestUser.SELF.id
        val OTHER_USER_ID = TestUser.OTHER_USER_ID
        val CONVERSATION_ID = TestConversation.CONVERSATION.id
        val OTHER_CONVERSATION_ID = ConversationId("other-conversation", "wire.com")
    }
}
