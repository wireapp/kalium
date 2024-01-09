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
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.feature.message.MarkMessagesAsNotifiedUseCase.UpdateTarget
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.util.DateTimeUtil.toIsoDateTimeString
import io.mockative.Mock
import io.mockative.any
import io.mockative.anything
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class MarkMessagesAsNotifiedUseCaseTest {

    @Test
    fun givenMarkIsCalledForAllConversations_whenInvokingTheUseCase_thenAllConversationsAreMarkedAsNotified() = runTest {
        val (arrangement, markMessagesAsNotified) = Arrangement()
            .withUpdatingAllConversationsReturning(Either.Right(Unit))
            .arrange()

        val result = markMessagesAsNotified(UpdateTarget.AllConversations)

        verify(arrangement.conversationRepository)
            .coroutine { updateAllConversationsNotificationDate() }
            .wasInvoked(exactly = once)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::updateConversationNotificationDate)
            .with(anything())
            .wasNotInvoked()

        assertEquals(result, Result.Success)
    }

    @Test
    fun givenMarkIsCalledWithSpecificConversationId_whenInvokingTheUseCase_thenSpecificConversationIsMarkedAsNotified() = runTest {
        val (arrangement, markMessagesAsNotified) = Arrangement()
            .withUpdatingOneConversationReturning(Either.Right(Unit))
            .arrange()

        val result = markMessagesAsNotified(UpdateTarget.SingleConversation(CONVERSATION_ID))

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::updateConversationNotificationDate)
            .with(eq(CONVERSATION_ID))
            .wasInvoked(exactly = once)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::updateAllConversationsNotificationDate)
            .wasNotInvoked()

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

        @Mock
        val conversationRepository: ConversationRepository = mock(classOf<ConversationRepository>())

        fun withUpdatingAllConversationsReturning(result: Either<StorageFailure, Unit>) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::updateAllConversationsNotificationDate)
                .whenInvoked()
                .thenReturn(result)
        }

        fun withUpdatingOneConversationReturning(result: Either<StorageFailure, Unit>) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::updateConversationNotificationDate)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun arrange() = this to MarkMessagesAsNotifiedUseCase(
            conversationRepository
        )

    }

    companion object {
        private val CONVERSATION_ID = QualifiedID("some_id", "some_domain")
    }
}
