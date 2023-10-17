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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GetConversationMessagesFromSearchQueryUseCaseTest {

    @Test
    fun givenSearchTermAndConversationId_whenInvokingUseCase_thenShouldCallMessageRepository() = runTest {
        // given
        val searchTerm = "message 1"
        val (arrangement, useCase) = Arrangement()
            .withRepositoryMessagesBySearchTermReturning(
                conversationId = CONVERSATION_ID,
                searchTerm = searchTerm,
                response = Either.Left(StorageFailure.DataNotFound)
            )
            .arrange()

        // when
        useCase(
            searchQuery = searchTerm,
            conversationId = CONVERSATION_ID
        )

        // then
        verify(arrangement.messageRepository)
            .coroutine { arrangement.messageRepository.getConversationMessagesFromSearch(searchTerm, CONVERSATION_ID) }
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenRepositoryFails_whenInvokingUseCase_thenShouldPropagateTheFailure() = runTest {
        // given
        val searchTerm = "message 1"
        val cause = StorageFailure.DataNotFound
        val (_, useCase) = Arrangement()
            .withRepositoryMessagesBySearchTermReturning(
                conversationId = CONVERSATION_ID,
                searchTerm = searchTerm,
                response = Either.Left(cause)
            )
            .arrange()

        // when
        val result = useCase(
            searchQuery = searchTerm,
            conversationId = CONVERSATION_ID
        )

        // then
        assertIs<Either.Left<StorageFailure.DataNotFound>>(result)
        assertEquals(cause, result.value)
    }

    @Test
    fun givenRepositorySucceeds_whenInvokingUseCase_thenShouldPropagateTheSuccess() = runTest {
        // given
        val searchTerm = "message 1"
        val (_, useCase) = Arrangement()
            .withRepositoryMessagesBySearchTermReturning(
                conversationId = CONVERSATION_ID,
                searchTerm = searchTerm,
                response = Either.Right(MESSAGES)
            )
            .arrange()

        // when
        val result = useCase(
            searchQuery = searchTerm,
            conversationId = CONVERSATION_ID
        )

        // then
        assertIs<Either.Right<List<Message.Standalone>>>(result)
    }

    private inner class Arrangement {

        @Mock
        val messageRepository: MessageRepository = mock(MessageRepository::class)

        private val getMessageById by lazy {
            GetConversationMessagesFromSearchQueryUseCaseImpl(messageRepository)
        }

        suspend fun withRepositoryMessagesBySearchTermReturning(
            conversationId: ConversationId,
            searchTerm: String,
            response: Either<CoreFailure, List<Message.Standalone>>
        ) = apply {
            given(messageRepository)
                .coroutine { messageRepository.getConversationMessagesFromSearch(searchTerm, conversationId) }
                .thenReturn(response)
        }

        fun arrange() = this to getMessageById
    }

    private companion object {
        val MESSAGES = listOf(TestMessage.TEXT_MESSAGE)
        val CONVERSATION_ID = TestConversation.ID
    }
}
