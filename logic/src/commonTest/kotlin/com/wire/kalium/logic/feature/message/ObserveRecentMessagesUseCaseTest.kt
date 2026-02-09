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

import app.cash.turbine.test
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestMessage
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ObserveRecentMessagesUseCaseTest {
    @Test
    fun givenConversation_whenInvoked_thenCallsRepositoryWithDefaults() = runTest {
        val messages = listOf<Message>(TestMessage.TEXT_MESSAGE)
        val (arrangement, useCase) = Arrangement()
            .withRecentMessagesReturning(CONVERSATION_ID, flowOf(messages))
            .arrange()

        useCase(CONVERSATION_ID)

        coVerify {
            arrangement.messageRepository.getMessagesByConversationIdAndVisibility(
                eq(CONVERSATION_ID),
                eq(100),
                eq(0),
                eq(Message.Visibility.entries),
            )
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenRepositoryFlow_whenInvoked_thenPropagatesMessages() = runTest {
        val messages = listOf<Message>(TestMessage.TEXT_MESSAGE)
        val (_, useCase) = Arrangement()
            .withRecentMessagesReturning(CONVERSATION_ID, flowOf(messages))
            .arrange()

        useCase(CONVERSATION_ID).test {
            assertEquals(messages, awaitItem())
            awaitComplete()
        }
    }

    private inner class Arrangement {
        val messageRepository: MessageRepository = mock(MessageRepository::class)

        private val observeRecentMessages by lazy {
            ObserveRecentMessagesUseCase(messageRepository)
        }

        suspend fun withRecentMessagesReturning(
            conversationId: ConversationId,
            response: Flow<List<Message>>,
        ) = apply {
            coEvery {
                messageRepository.getMessagesByConversationIdAndVisibility(
                    eq(conversationId),
                    any(),
                    any(),
                    any(),
                )
            }.returns(response)
        }

        fun arrange() = this to observeRecentMessages
    }

    private companion object {
        val CONVERSATION_ID = TestConversation.ID
    }
}
