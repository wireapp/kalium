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
package com.wire.kalium.logic.feature.message.message

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.feature.message.GetSearchedConversationMessagePositionUseCase
import com.wire.kalium.logic.feature.message.GetSearchedConversationMessagePositionUseCaseImpl
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import com.wire.kalium.util.KaliumDispatcher
import io.mockative.Mock
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GetSearchedConversationMessagePositionUseCaseTest {

    @Test
    fun givenConversationIdAndMessageId_whenInvokingUseCase_thenShouldCallMessageRepository() = runTest {
        val (arrangement, getSearchedConversationMessagePosition) = Arrangement(testKaliumDispatcher)
            .withRepositoryMessagePositionReturning(
                conversationId = CONVERSATION_ID,
                messageId = MESSAGE_ID,
                response = Either.Left(StorageFailure.DataNotFound)
            )
            .arrange()

        getSearchedConversationMessagePosition(
            conversationId = CONVERSATION_ID,
            messageId = MESSAGE_ID
        )

        coVerify {
            arrangement.messageRepository.getSearchedConversationMessagePosition(
                conversationId = CONVERSATION_ID,
                messageId = MESSAGE_ID
            )
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenRepositoryFails_whenInvokingUseCase_thenShouldPropagateTheFailure() = runTest {
        val cause = StorageFailure.DataNotFound
        val (_, getSearchedConversationMessagePosition) = Arrangement(testKaliumDispatcher)
            .withRepositoryMessagePositionReturning(
                conversationId = CONVERSATION_ID,
                messageId = MESSAGE_ID,
                response = Either.Left(StorageFailure.DataNotFound)
            )
            .arrange()

        val result = getSearchedConversationMessagePosition(
            conversationId = CONVERSATION_ID,
            messageId = MESSAGE_ID
        )

        assertIs<GetSearchedConversationMessagePositionUseCase.Result.Failure>(result)
        assertEquals(cause, result.cause)
    }

    @Test
    fun givenRepositorySucceeds_whenInvokingUseCase_thenShouldPropagateTheSuccess() = runTest {
        val expectedMessagePosition = 113
        val (_, getSearchedConversationMessagePosition) = Arrangement(testKaliumDispatcher)
            .withRepositoryMessagePositionReturning(
                conversationId = CONVERSATION_ID,
                messageId = MESSAGE_ID,
                response = Either.Right(expectedMessagePosition)
            )
            .arrange()

        val result = getSearchedConversationMessagePosition(
            conversationId = CONVERSATION_ID,
            messageId = MESSAGE_ID
        )

        assertIs<GetSearchedConversationMessagePositionUseCase.Result.Success>(result)
        assertEquals(expectedMessagePosition, result.position)
    }

    private inner class Arrangement(var dispatcher: KaliumDispatcher = TestKaliumDispatcher) {

        @Mock
        val messageRepository: MessageRepository = mock(MessageRepository::class)

        private val getSearchedConversationMessagePosition by lazy {
            GetSearchedConversationMessagePositionUseCaseImpl(
                messageRepository = messageRepository,
                dispatcher = dispatcher
            )
        }

        suspend fun withRepositoryMessagePositionReturning(
            conversationId: ConversationId,
            messageId: String,
            response: Either<StorageFailure, Int>
        ) = apply {
            coEvery {
                messageRepository.getSearchedConversationMessagePosition(
                    conversationId = conversationId,
                    messageId = messageId
                )
            }.returns(response)
        }

        fun arrange() = this to getSearchedConversationMessagePosition
    }

    private companion object {
        const val MESSAGE_ID = TestMessage.TEST_MESSAGE_ID
        val MESSAGE = TestMessage.TEXT_MESSAGE
        val CONVERSATION_ID = TestConversation.ID
    }
}
