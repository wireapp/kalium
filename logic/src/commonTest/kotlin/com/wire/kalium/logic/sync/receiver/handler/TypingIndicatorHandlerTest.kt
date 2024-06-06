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
package com.wire.kalium.logic.sync.receiver.handler

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.TypingIndicatorIncomingRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.util.shouldSucceed
import io.mockative.Mock
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class TypingIndicatorHandlerTest {

    @Test
    fun givenTypingEventStarted_whenIsSelfUser_thenSkipIt() = runTest {
        val (arrangement, handler) = Arrangement()
            .withTypingIndicatorObserve(setOf(TestUser.SELF.id))
            .arrange()

        val result = handler.handle(TestEvent.typingIndicator(Conversation.TypingIndicatorMode.STARTED))

        result.shouldSucceed()
        coVerify {
            arrangement.typingIndicatorIncomingRepository.addTypingUserInConversation(eq(TestConversation.ID), eq(TestUser.SELF.id))
        }.wasNotInvoked()
    }

    @Test
    fun givenTypingEvent_whenIsModeStarted_thenHandleToAdd() = runTest {
        val (arrangement, handler) = Arrangement()
            .withTypingIndicatorObserve(setOf(TestUser.OTHER_USER_ID))
            .arrange()

        val result = handler.handle(TestEvent.typingIndicator(Conversation.TypingIndicatorMode.STARTED))

        result.shouldSucceed()
        coVerify {
            arrangement.typingIndicatorIncomingRepository.addTypingUserInConversation(eq(TestConversation.ID), eq(TestUser.OTHER_USER_ID))
        }.wasInvoked(once)
    }

    @Test
    fun givenTypingEvent_whenIsModeStopped_thenHandleToRemove() = runTest {
        val (arrangement, handler) = Arrangement()
            .withTypingIndicatorObserve(setOf(TestUser.OTHER_USER_ID))
            .arrange()

        val result = handler.handle(TestEvent.typingIndicator(Conversation.TypingIndicatorMode.STOPPED))

        result.shouldSucceed()
        coVerify {
            arrangement.typingIndicatorIncomingRepository.removeTypingUserInConversation(eq(TestConversation.ID), eq(TestUser.OTHER_USER_ID))
        }.wasInvoked(once)
    }

    @Test
    fun givenTypingEventStopped_whenIsSelfUser_thenSkipIt() = runTest {
        val (arrangement, handler) = Arrangement()
            .withTypingIndicatorObserve(setOf(TestUser.SELF.id))
            .arrange()

        val result = handler.handle(TestEvent.typingIndicator(Conversation.TypingIndicatorMode.STOPPED))

        result.shouldSucceed()
        coVerify {
            arrangement.typingIndicatorIncomingRepository.removeTypingUserInConversation(eq(TestConversation.ID), eq(TestUser.SELF.id))
        }.wasNotInvoked()
    }

    private class Arrangement {
        @Mock
        val typingIndicatorIncomingRepository: TypingIndicatorIncomingRepository = mock(TypingIndicatorIncomingRepository::class)

        suspend fun withTypingIndicatorObserve(usersId: Set<UserId>) = apply {
            coEvery {
                typingIndicatorIncomingRepository.observeUsersTyping(eq(TestConversation.ID))
            }.returns(flowOf(usersId))
        }

        fun arrange() = this to TypingIndicatorHandlerImpl(TestUser.SELF.id, typingIndicatorIncomingRepository)
    }

}
