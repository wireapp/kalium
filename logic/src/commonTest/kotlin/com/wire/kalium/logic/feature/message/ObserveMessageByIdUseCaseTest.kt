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
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.util.KaliumDispatcher
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ObserveMessageByIdUseCaseTest {

    private val testDispatchers: KaliumDispatcher = TestKaliumDispatcher

    @Test
    fun givenMessageAndConversationId_whenInvokingUseCase_thenShouldCallMessageRepository() = runTest(testDispatchers.io) {
        val message = MESSAGE
        val (arrangement, useCase) = Arrangement()
            .withMessageByIdReturning(message.conversationId, message.id, flowOf(message.right()))
            .arrange()

        useCase(message.conversationId, message.id)

        coVerify {
            arrangement.messageRepository.observeMessageById(message.conversationId, message.id)
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenMessageNotExists_whenInvokingUseCase_thenShouldPropagateTheFailure() = runTest(testDispatchers.io) {
        val cause = StorageFailure.DataNotFound
        val (_, useCase) = Arrangement()
            .withMessageByIdReturning(CONVERSATION_ID, MESSAGE_ID, flowOf(cause.left()))
            .arrange()

        useCase(CONVERSATION_ID, MESSAGE_ID).test {
            awaitItem().let {
                assertIs<ObserveMessageByIdUseCase.Result.Failure>(it)
                assertEquals(cause, it.cause)
            }
            awaitComplete()
        }
    }

    @Test
    fun givenMessageExists_whenInvokingUseCase_thenShouldPropagateTheSuccess() = runTest(testDispatchers.io) {
        val message = MESSAGE
        val (_, useCase) = Arrangement()
            .withMessageByIdReturning(message.conversationId, message.id, flowOf(message.right()))
            .arrange()

        useCase(message.conversationId, message.id).test {
            awaitItem().let {
                assertIs<ObserveMessageByIdUseCase.Result.Success>(it)
                assertEquals(message, it.message)
            }
            awaitComplete()
        }
    }

    @Test
    fun givenMessageIsUpdated_whenInvokingUseCase_thenShouldPropagateChange() = runTest(testDispatchers.io) {
        val message = MESSAGE.copy(status = Message.Status.Sent)
        val updatedMessage = message.copy(status = Message.Status.Read(1L))
        val (_, useCase) = Arrangement()
            .withMessageByIdReturning(message.conversationId, message.id, flowOf(message.right(), updatedMessage.right()))
            .arrange()

        useCase(message.conversationId, message.id).test {
            awaitItem().let {
                assertIs<ObserveMessageByIdUseCase.Result.Success>(it)
                assertEquals(message, it.message)
                assertEquals(message.status, it.message.status)
            }
            awaitItem().let {
                assertIs<ObserveMessageByIdUseCase.Result.Success>(it)
                assertEquals(updatedMessage, it.message)
                assertEquals(updatedMessage.status, it.message.status)
            }
            awaitComplete()
        }
    }

    @Test
    fun givenMessageIsRemoved_whenInvokingUseCase_thenShouldPropagateChange() = runTest(testDispatchers.io) {
        val message = MESSAGE
        val cause = StorageFailure.DataNotFound
        val (_, useCase) = Arrangement()
            .withMessageByIdReturning(message.conversationId, message.id, flowOf(message.right(), cause.left()))
            .arrange()

        useCase(message.conversationId, message.id).test {
            awaitItem().let {
                assertIs<ObserveMessageByIdUseCase.Result.Success>(it)
                assertEquals(message, it.message)
                assertEquals(message.status, it.message.status)
            }
            awaitItem().let {
                assertIs<ObserveMessageByIdUseCase.Result.Failure>(it)
                assertEquals(cause, it.cause)
            }
            awaitComplete()
        }
    }

    private inner class Arrangement {
        val messageRepository: MessageRepository = mock(MessageRepository::class)

        private val observeMessageById by lazy {
            ObserveMessageByIdUseCase(messageRepository, testDispatchers)
        }

        suspend fun withMessageByIdReturning(
            conversationId: ConversationId,
            messageId: String,
            response: Flow<Either<StorageFailure, Message>>
        ) = apply {
            coEvery {
                messageRepository.observeMessageById(conversationId, messageId)
            }.returns(response)
        }

        fun arrange() = this to observeMessageById
    }

    private companion object {
        const val MESSAGE_ID = TestMessage.TEST_MESSAGE_ID
        val MESSAGE = TestMessage.TEXT_MESSAGE
        val CONVERSATION_ID = TestConversation.ID
    }
}
