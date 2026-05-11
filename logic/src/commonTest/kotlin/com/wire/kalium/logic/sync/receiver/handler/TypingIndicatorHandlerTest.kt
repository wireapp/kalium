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
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
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
        verifySuspend(VerifyMode.not) {
            arrangement.typingIndicatorIncomingRepository.addTypingUserInConversation(eq(TestConversation.ID), eq(TestUser.SELF.id))
        }
    }

    @Test
    fun givenTypingEvent_whenIsModeStarted_thenHandleToAdd() = runTest {
        val (arrangement, handler) = Arrangement()
            .withTypingIndicatorObserve(setOf(TestUser.OTHER_USER_ID))
            .arrange()

        val result = handler.handle(TestEvent.typingIndicator(Conversation.TypingIndicatorMode.STARTED))

        result.shouldSucceed()
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.typingIndicatorIncomingRepository.addTypingUserInConversation(eq(TestConversation.ID), eq(TestUser.OTHER_USER_ID))
        }
    }

    @Test
    fun givenTypingEvent_whenIsModeStopped_thenHandleToRemove() = runTest {
        val (arrangement, handler) = Arrangement()
            .withTypingIndicatorObserve(setOf(TestUser.OTHER_USER_ID))
            .arrange()

        val result = handler.handle(TestEvent.typingIndicator(Conversation.TypingIndicatorMode.STOPPED))

        result.shouldSucceed()
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.typingIndicatorIncomingRepository.removeTypingUserInConversation(eq(TestConversation.ID), eq(TestUser.OTHER_USER_ID))
        }
    }

    @Test
    fun givenTypingEventStopped_whenIsSelfUser_thenSkipIt() = runTest {
        val (arrangement, handler) = Arrangement()
            .withTypingIndicatorObserve(setOf(TestUser.SELF.id))
            .arrange()

        val result = handler.handle(TestEvent.typingIndicator(Conversation.TypingIndicatorMode.STOPPED))

        result.shouldSucceed()
        verifySuspend(VerifyMode.not) {
            arrangement.typingIndicatorIncomingRepository.removeTypingUserInConversation(eq(TestConversation.ID), eq(TestUser.SELF.id))
        }
    }

    private class Arrangement {
        val typingIndicatorIncomingRepository: TypingIndicatorIncomingRepository = mock(mode = MockMode.autoUnit)

        suspend fun withTypingIndicatorObserve(usersId: Set<UserId>) = apply {
            everySuspend {
                typingIndicatorIncomingRepository.observeUsersTyping(eq(TestConversation.ID))
            } returns flowOf(usersId)
        }

        fun arrange() = this to TypingIndicatorHandlerImpl(TestUser.SELF.id, typingIndicatorIncomingRepository)
    }

}
