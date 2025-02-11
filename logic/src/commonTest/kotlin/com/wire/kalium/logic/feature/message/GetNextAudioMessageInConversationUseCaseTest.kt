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

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
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

class GetNextAudioMessageInConversationUseCaseTest {

    private val testDispatchers: KaliumDispatcher = TestKaliumDispatcher

    @Test
    fun givenMessageAndConversationId_whenInvokingUseCase_thenShouldCallMessageRepository() = runTest(testDispatchers.io) {
        val (arrangement, getMessageByIdUseCase) = Arrangement()
            .withRepositoryMessageByIdReturning(CONVERSATION_ID, MESSAGE_ID, Either.Left(StorageFailure.DataNotFound))
            .arrange()

        getMessageByIdUseCase(CONVERSATION_ID, MESSAGE_ID)

        coVerify {
            arrangement.messageRepository.getMessageById(CONVERSATION_ID, MESSAGE_ID)
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenRepositoryFails_whenInvokingUseCase_thenShouldPropagateTheFailure() = runTest(testDispatchers.io) {
        val cause = StorageFailure.DataNotFound
        val (_, getMessageByIdUseCase) = Arrangement()
            .withRepositoryMessageByIdReturning(CONVERSATION_ID, MESSAGE_ID, Either.Left(cause))
            .arrange()

        val result = getMessageByIdUseCase(CONVERSATION_ID, MESSAGE_ID)

        assertIs<GetMessageByIdUseCase.Result.Failure>(result)
        assertEquals(cause, result.cause)
    }

    @Test
    fun givenRepositorySucceeds_whenInvokingUseCase_thenShouldPropagateTheSuccess() = runTest(testDispatchers.io) {
        val (_, getMessageByIdUseCase) = Arrangement()
            .withRepositoryMessageByIdReturning(CONVERSATION_ID, MESSAGE_ID, Either.Right(MESSAGE))
            .arrange()

        val result = getMessageByIdUseCase(CONVERSATION_ID, MESSAGE_ID)

        assertIs<GetMessageByIdUseCase.Result.Success>(result)
        assertEquals(MESSAGE, result.message)
    }

    private inner class Arrangement {

        @Mock
        val messageRepository: MessageRepository = mock(MessageRepository::class)

        private val getMessageById by lazy {
            GetMessageByIdUseCase(messageRepository, testDispatchers)
        }

        suspend fun withRepositoryMessageByIdReturning(
            conversationId: ConversationId,
            messageId: String,
            response: Either<StorageFailure, Message>
        ) = apply {
            coEvery {
                messageRepository.getMessageById(conversationId, messageId)
            }.returns(response)
        }

        fun arrange() = this to getMessageById
    }

    private companion object {
        const val MESSAGE_ID = TestMessage.TEST_MESSAGE_ID
        val MESSAGE = TestMessage.TEXT_MESSAGE
        val CONVERSATION_ID = TestConversation.ID
    }
}
