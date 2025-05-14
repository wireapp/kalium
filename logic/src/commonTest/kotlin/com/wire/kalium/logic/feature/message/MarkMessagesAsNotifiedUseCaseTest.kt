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
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.feature.message.MarkMessagesAsNotifiedUseCase.UpdateTarget
import com.wire.kalium.common.functional.Either
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MarkMessagesAsNotifiedUseCaseTest {

    @Test
    fun givenMarkIsCalledForAllConversations_whenInvokingTheUseCase_thenAllConversationsAreMarkedAsNotified() = runTest {
        val (arrangement, markMessagesAsNotified) = Arrangement()
            .withUpdatingAllConversationsReturning(Either.Right(Unit))
            .arrange()

        val result = markMessagesAsNotified(UpdateTarget.AllConversations)

        coVerify {
            arrangement.conversationRepository.updateAllConversationsNotificationDate()
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.conversationRepository.updateConversationNotificationDate(any())
        }.wasNotInvoked()

        assertEquals(result, Result.Success)
    }

    @Test
    fun givenMarkIsCalledWithSpecificConversationId_whenInvokingTheUseCase_thenSpecificConversationIsMarkedAsNotified() = runTest {
        val (arrangement, markMessagesAsNotified) = Arrangement()
            .withUpdatingOneConversationReturning(Either.Right(Unit))
            .arrange()

        val result = markMessagesAsNotified(UpdateTarget.SingleConversation(CONVERSATION_ID))

        coVerify {
            arrangement.conversationRepository.updateConversationNotificationDate(eq(CONVERSATION_ID))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.conversationRepository.updateAllConversationsNotificationDate()
        }.wasNotInvoked()

        assertEquals(result, Result.Success)
    }

    @Test
    fun givenUpdatingOneConversationFails_whenInvokingTheUseCase_thenFailureIsPropagated() = runTest {
        val failure = StorageFailure.DataNotFound

        val (_, markMessagesAsNotified) = Arrangement()
            .withUpdatingOneConversationReturning(Either.Left(failure))
            .arrange()

        val result = markMessagesAsNotified(UpdateTarget.SingleConversation(CONVERSATION_ID))

        assertEquals(result, Result.Failure(failure))
    }

    @Test
    fun givenUpdatingAllConversationsFails_whenInvokingTheUseCase_thenFailureIsPropagated() = runTest {
        val failure = StorageFailure.DataNotFound

        val (_, markMessagesAsNotified) = Arrangement()
            .withUpdatingAllConversationsReturning(Either.Left(failure))
            .arrange()

        val result = markMessagesAsNotified(UpdateTarget.AllConversations)

        assertEquals(result, Result.Failure(failure))
    }

    private class Arrangement {

        val conversationRepository: ConversationRepository = mock(ConversationRepository::class)

        suspend fun withUpdatingAllConversationsReturning(result: Either<StorageFailure, Unit>) = apply {
            coEvery {
                conversationRepository.updateAllConversationsNotificationDate()
            }.returns(result)
        }

        suspend fun withUpdatingOneConversationReturning(result: Either<StorageFailure, Unit>) = apply {
            coEvery {
                conversationRepository.updateConversationNotificationDate(any())
            }.returns(result)
        }

        fun arrange() = this to MarkMessagesAsNotifiedUseCase(
            conversationRepository
        )

    }

    companion object {
        private val CONVERSATION_ID = QualifiedID("some_id", "some_domain")
    }
}
