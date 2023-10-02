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
package com.wire.kalium.logic.sync.receiver.handler

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ExpiringUserTyping
import com.wire.kalium.logic.data.conversation.TypingIndicatorRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.util.shouldSucceed
import io.mockative.Mock
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test

class TypingIndicatorHandlerTest {

    @Test
    fun givenTypingEventStarted_whenIsSelfUser_thenSkipIt() = runTest {
        val (arrangement, handler) = Arrangement()
            .withTypingIndicatorObserve(setOf(TestUser.SELF.id))
            .arrange()

        val result = handler.handle(TestEvent.typingIndicator(Conversation.TypingIndicatorMode.STARTED))

        result.shouldSucceed()
        verify(arrangement.typingIndicatorRepository)
            .function(arrangement.typingIndicatorRepository::addTypingUserInConversation)
            .with(eq(TestConversation.ID), eq(TestUser.USER_ID))
            .wasNotInvoked()
    }

    @Test
    fun givenTypingEvent_whenIsModeStarted_thenHandleToAdd() = runTest {
        val (arrangement, handler) = Arrangement()
            .withTypingIndicatorObserve(setOf(TestUser.USER_ID))
            .arrange()

        val result = handler.handle(TestEvent.typingIndicator(Conversation.TypingIndicatorMode.STARTED))

        result.shouldSucceed()
        verify(arrangement.typingIndicatorRepository)
            .function(arrangement.typingIndicatorRepository::addTypingUserInConversation)
            .with(eq(TestConversation.ID), eq(TestUser.USER_ID))
            .wasInvoked(once)
    }

    @Test
    fun givenTypingEvent_whenIsModeStopped_thenHandleToRemove() = runTest {
        val (arrangement, handler) = Arrangement()
            .withTypingIndicatorObserve(setOf(TestUser.USER_ID))
            .arrange()

        val result = handler.handle(TestEvent.typingIndicator(Conversation.TypingIndicatorMode.STOPPED))

        result.shouldSucceed()
        verify(arrangement.typingIndicatorRepository)
            .function(arrangement.typingIndicatorRepository::removeTypingUserInConversation)
            .with(eq(TestConversation.ID), eq(TestUser.USER_ID))
            .wasInvoked(once)
    }

    @Test
    fun givenTypingEventStopped_whenIsSelfUser_thenSkipIt() = runTest {
        val (arrangement, handler) = Arrangement()
            .withTypingIndicatorObserve(setOf(TestUser.USER_ID))
            .arrange()

        val result = handler.handle(TestEvent.typingIndicator(Conversation.TypingIndicatorMode.STOPPED))

        result.shouldSucceed()
        verify(arrangement.typingIndicatorRepository)
            .function(arrangement.typingIndicatorRepository::removeTypingUserInConversation)
            .with(eq(TestConversation.ID), eq(TestUser.USER_ID))
            .wasNotInvoked()
    }

    private class Arrangement {
        @Mock
        val typingIndicatorRepository: TypingIndicatorRepository = mock(TypingIndicatorRepository::class)

        fun withTypingIndicatorObserve(usersId: Set<UserId>) = apply {
            given(typingIndicatorRepository)
                .suspendFunction(typingIndicatorRepository::observeUsersTyping)
                .whenInvokedWith(eq(TestConversation.ID))
                .thenReturn(flowOf(usersId.map { ExpiringUserTyping(it, Clock.System.now()) }.toSet()))
        }

        fun arrange() = this to TypingIndicatorHandlerImpl(TestUser.SELF.id, typingIndicatorRepository)
    }

}
