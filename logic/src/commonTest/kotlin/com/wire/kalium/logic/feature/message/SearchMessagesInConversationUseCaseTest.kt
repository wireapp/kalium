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

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.util.KaliumDispatcher
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SearchMessagesInConversationUseCaseTest {

    private val testDispatchers: KaliumDispatcher = TestKaliumDispatcher

    @Test
    fun givenSearchQuery_whenInvokingUseCase_thenShouldCallMessageRepositoryWithCorrectParams() = runTest(testDispatchers.io) {
        val (arrangement, searchMessagesUseCase) = Arrangement()
            .withSearchMessagesReturning(CONVERSATION_ID, SEARCH_QUERY, Either.Right(emptyList()))
            .arrange()

        searchMessagesUseCase(CONVERSATION_ID, SEARCH_QUERY, DEFAULT_LIMIT, DEFAULT_OFFSET)

        coVerify {
            arrangement.messageRepository.searchMessagesByText(
                eq(CONVERSATION_ID),
                eq(SEARCH_QUERY),
                eq(DEFAULT_LIMIT),
                eq(DEFAULT_OFFSET)
            )
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenCustomLimitAndOffset_whenInvokingUseCase_thenShouldPassThemToRepository() = runTest(testDispatchers.io) {
        val customLimit = 50
        val customOffset = 10
        val (arrangement, searchMessagesUseCase) = Arrangement()
            .withSearchMessagesReturning(CONVERSATION_ID, SEARCH_QUERY, Either.Right(emptyList()))
            .arrange()

        searchMessagesUseCase(CONVERSATION_ID, SEARCH_QUERY, customLimit, customOffset)

        coVerify {
            arrangement.messageRepository.searchMessagesByText(
                eq(CONVERSATION_ID),
                eq(SEARCH_QUERY),
                eq(customLimit),
                eq(customOffset)
            )
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenRepositoryFails_whenInvokingUseCase_thenShouldReturnFailure() = runTest(testDispatchers.io) {
        val cause = StorageFailure.DataNotFound
        val (_, searchMessagesUseCase) = Arrangement()
            .withSearchMessagesReturning(CONVERSATION_ID, SEARCH_QUERY, Either.Left(cause))
            .arrange()

        val result = searchMessagesUseCase(CONVERSATION_ID, SEARCH_QUERY)

        assertIs<SearchMessagesInConversationUseCase.Result.Failure>(result)
        assertEquals(cause, result.cause)
    }

    @Test
    fun givenRepositoryReturnsEmptyList_whenInvokingUseCase_thenShouldReturnSuccessWithEmptyList() = runTest(testDispatchers.io) {
        val (_, searchMessagesUseCase) = Arrangement()
            .withSearchMessagesReturning(CONVERSATION_ID, SEARCH_QUERY, Either.Right(emptyList()))
            .arrange()

        val result = searchMessagesUseCase(CONVERSATION_ID, SEARCH_QUERY)

        assertIs<SearchMessagesInConversationUseCase.Result.Success>(result)
        assertTrue(result.messages.isEmpty())
    }

    @Test
    fun givenRepositoryReturnsMessages_whenInvokingUseCase_thenShouldReturnSuccessWithMessages() = runTest(testDispatchers.io) {
        val messages = listOf(
            createTextMessage("msg1", "Hello world"),
            createTextMessage("msg2", "Hello there")
        )
        val (_, searchMessagesUseCase) = Arrangement()
            .withSearchMessagesReturning(CONVERSATION_ID, SEARCH_QUERY, Either.Right(messages))
            .arrange()

        val result = searchMessagesUseCase(CONVERSATION_ID, SEARCH_QUERY)

        assertIs<SearchMessagesInConversationUseCase.Result.Success>(result)
        assertEquals(2, result.messages.size)
        assertEquals(messages, result.messages)
    }

    @Test
    fun givenMultipleMessages_whenSearching_thenShouldPreserveMessageOrder() = runTest(testDispatchers.io) {
        val messages = listOf(
            createTextMessage("msg1", "First message"),
            createTextMessage("msg2", "Second message"),
            createTextMessage("msg3", "Third message")
        )
        val (_, searchMessagesUseCase) = Arrangement()
            .withSearchMessagesReturning(CONVERSATION_ID, SEARCH_QUERY, Either.Right(messages))
            .arrange()

        val result = searchMessagesUseCase(CONVERSATION_ID, SEARCH_QUERY)

        assertIs<SearchMessagesInConversationUseCase.Result.Success>(result)
        assertEquals("msg1", result.messages[0].id)
        assertEquals("msg2", result.messages[1].id)
        assertEquals("msg3", result.messages[2].id)
    }

    @Test
    fun givenDefaultParameters_whenInvokingUseCase_thenShouldUseDefaultLimitAndOffset() = runTest(testDispatchers.io) {
        val (arrangement, searchMessagesUseCase) = Arrangement()
            .withSearchMessagesReturning(CONVERSATION_ID, SEARCH_QUERY, Either.Right(emptyList()))
            .arrange()

        // Call without specifying limit and offset
        searchMessagesUseCase(CONVERSATION_ID, SEARCH_QUERY)

        coVerify {
            arrangement.messageRepository.searchMessagesByText(
                eq(CONVERSATION_ID),
                eq(SEARCH_QUERY),
                eq(100),  // default limit
                eq(0)     // default offset
            )
        }.wasInvoked(exactly = once)
    }

    private inner class Arrangement {
        val messageRepository: MessageRepository = mock(MessageRepository::class)

        private val searchMessagesInConversation by lazy {
            SearchMessagesInConversationUseCaseImpl(messageRepository, testDispatchers)
        }

        suspend fun withSearchMessagesReturning(
            conversationId: ConversationId,
            searchQuery: String,
            response: Either<StorageFailure, List<Message.Standalone>>
        ) = apply {
            coEvery {
                messageRepository.searchMessagesByText(
                    eq(conversationId),
                    eq(searchQuery),
                    any(),
                    any()
                )
            }.returns(response)
        }

        fun arrange() = this to searchMessagesInConversation
    }

    private companion object {
        val CONVERSATION_ID = TestConversation.ID
        const val SEARCH_QUERY = "hello"
        const val DEFAULT_LIMIT = 100
        const val DEFAULT_OFFSET = 0

        fun createTextMessage(id: String, text: String): Message.Regular = Message.Regular(
            id = id,
            content = MessageContent.Text(text),
            conversationId = CONVERSATION_ID,
            date = TestMessage.TEST_DATE,
            senderUserId = TestUser.USER_ID,
            senderClientId = TestClient.CLIENT_ID,
            status = Message.Status.Sent,
            editStatus = Message.EditStatus.NotEdited,
            isSelfMessage = false
        )
    }
}
