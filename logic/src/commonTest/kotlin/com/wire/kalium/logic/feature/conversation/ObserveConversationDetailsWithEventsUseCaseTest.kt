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

package com.wire.kalium.logic.feature.conversation

import app.cash.turbine.test
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.conversation.ConversationDetailsWithEvents
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestConversationDetails
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

class ObserveConversationDetailsWithEventsUseCaseTest {

    private val testDispatchers: KaliumDispatcher = TestKaliumDispatcher

    @Test
    fun givenConversationId_whenInvokingUseCase_thenShouldCallConversationRepository() = runTest(testDispatchers.io) {
        val conversation = CONVERSATION_DETTAILS_WITH_EVENTS()
        val (arrangement, useCase) = Arrangement()
            .withConversationDetailsWithEventsByIdReturning(conversation.id, flowOf(conversation.right()))
            .arrange()

        useCase(conversation.id)

        coVerify {
            arrangement.conversationRepository.observeConversationDetailsWithEventsById(conversation.id)
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenMessageNotExists_whenInvokingUseCase_thenShouldPropagateTheFailure() = runTest(testDispatchers.io) {
        val cause = StorageFailure.DataNotFound
        val (_, useCase) = Arrangement()
            .withConversationDetailsWithEventsByIdReturning(CONVERSATION_DETTAILS_WITH_EVENTS().id, flowOf(cause.left()))
            .arrange()

        useCase(CONVERSATION_DETTAILS_WITH_EVENTS().id).test {
            awaitItem().let {
                assertIs<ObserveConversationDetailsWithEventsUseCase.Result.Failure>(it)
                assertEquals(cause, it.cause)
            }
            awaitComplete()
        }
    }

    @Test
    fun givenMessageExists_whenInvokingUseCase_thenShouldPropagateTheSuccess() = runTest(testDispatchers.io) {
        val conversation = CONVERSATION_DETTAILS_WITH_EVENTS()
        val (_, useCase) = Arrangement()
            .withConversationDetailsWithEventsByIdReturning(conversation.id, flowOf(conversation.right()))
            .arrange()

        useCase(conversation.id).test {
            awaitItem().let {
                assertIs<ObserveConversationDetailsWithEventsUseCase.Result.Success>(it)
                assertEquals(conversation, it.conversationDetailsWithEvents)
            }
            awaitComplete()
        }
    }

    @Test
    fun givenMessageIsUpdated_whenInvokingUseCase_thenShouldPropagateChange() = runTest(testDispatchers.io) {
        val conversation = CONVERSATION_DETTAILS_WITH_EVENTS(archived = false)
        val updatedConversation = CONVERSATION_DETTAILS_WITH_EVENTS(archived = true)
        val (_, useCase) = Arrangement()
            .withConversationDetailsWithEventsByIdReturning(conversation.id, flowOf(conversation.right(), updatedConversation.right()))
            .arrange()

        useCase(conversation.id).test {
            awaitItem().let {
                assertIs<ObserveConversationDetailsWithEventsUseCase.Result.Success>(it)
                assertEquals(conversation, it.conversationDetailsWithEvents)
            }
            awaitItem().let {
                assertIs<ObserveConversationDetailsWithEventsUseCase.Result.Success>(it)
                assertEquals(updatedConversation, it.conversationDetailsWithEvents)
            }
            awaitComplete()
        }
    }

    @Test
    fun givenMessageIsRemoved_whenInvokingUseCase_thenShouldPropagateChange() = runTest(testDispatchers.io) {
        val conversation = CONVERSATION_DETTAILS_WITH_EVENTS(archived = false)
        val cause = StorageFailure.DataNotFound
        val (_, useCase) = Arrangement()
            .withConversationDetailsWithEventsByIdReturning(conversation.id,flowOf(conversation.right(), cause.left()))
            .arrange()

        useCase(conversation.id).test {
            awaitItem().let {
                assertIs<ObserveConversationDetailsWithEventsUseCase.Result.Success>(it)
                assertEquals(conversation, it.conversationDetailsWithEvents)
            }
            awaitItem().let {
                assertIs<ObserveConversationDetailsWithEventsUseCase.Result.Failure>(it)
                assertEquals(cause, it.cause)
            }
            awaitComplete()
        }
    }

    private inner class Arrangement {
        val conversationRepository: ConversationRepository = mock(ConversationRepository::class)

        private val observeConversationDetailsWithEvents by lazy {
            ObserveConversationDetailsWithEventsUseCase(conversationRepository, testDispatchers)
        }

        suspend fun withConversationDetailsWithEventsByIdReturning(
            conversationId: ConversationId,
            response: Flow<Either<StorageFailure, ConversationDetailsWithEvents>>
        ) = apply {
            coEvery {
                conversationRepository.observeConversationDetailsWithEventsById(conversationId)
            }.returns(response)
        }

        fun arrange() = this to observeConversationDetailsWithEvents
    }

    private companion object {
        fun CONVERSATION_DETTAILS_WITH_EVENTS(archived: Boolean = false) = ConversationDetailsWithEvents(
            TestConversationDetails.CONVERSATION_GROUP.copy(conversation = TestConversation.GROUP().copy(archived = archived)),
        )
    }
}

private val ConversationDetailsWithEvents.id get() = conversationDetails.conversation.id
