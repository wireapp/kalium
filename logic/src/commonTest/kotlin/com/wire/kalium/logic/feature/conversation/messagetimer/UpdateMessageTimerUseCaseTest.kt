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
package com.wire.kalium.logic.feature.conversation.messagetimer

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateMessageTimerUseCaseTest {

    @Test
    fun givenConversationAndTimer_WhenUpdateMessageTimerIsSuccessful_ThenReturnSuccess() = runTest {
        val (arrangement, updateMessageTimerUseCase) = Arrangement()
            .withUpdateMessageTimer(Either.Right(Unit))
            .arrange()

        val messageTimer = 5000L

        val result = updateMessageTimerUseCase(TestConversation.ID, messageTimer)

        assertIs<UpdateMessageTimerUseCase.Result.Success>(result)

        verify(arrangement.conversationGroupRepository)
            .suspendFunction(arrangement.conversationGroupRepository::updateMessageTimer)
            .with(eq(TestConversation.ID), eq(messageTimer))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenConversationAndTimer_WhenUpdateMessageTimerFailed_ThenReturnFailure() = runTest {
        val (arrangement, updateMessageTimerUseCase) = Arrangement()
            .withUpdateMessageTimer(Either.Left(StorageFailure.DataNotFound))
            .arrange()

        val messageTimer = 5000L

        val result = updateMessageTimerUseCase(TestConversation.ID, messageTimer)
        assertIs<UpdateMessageTimerUseCase.Result.Failure>(result)

        verify(arrangement.conversationGroupRepository)
            .suspendFunction(arrangement.conversationGroupRepository::updateMessageTimer)
            .with(eq(TestConversation.ID), eq(messageTimer))
            .wasInvoked(exactly = once)
    }

    private class Arrangement {

        @Mock
        val conversationGroupRepository = mock(classOf<ConversationGroupRepository>())

        private val updateMessageTimerUseCase = UpdateMessageTimerUseCaseImpl(
            conversationGroupRepository
        )

        fun withUpdateMessageTimer(either: Either<CoreFailure, Unit>) = apply {
            given(conversationGroupRepository)
                .suspendFunction(conversationGroupRepository::updateMessageTimer)
                .whenInvokedWith(any(), any())
                .thenReturn(either)
        }

        fun arrange() = this to updateMessageTimerUseCase
    }
}
