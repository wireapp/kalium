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
package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import io.mockative.Mock
import io.mockative.any
import io.mockative.given
import io.mockative.mock
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class TypingIndicatorOutgoingRepositoryTest {

    @Test
    fun givenAStartedTypingEvent_whenUserConfigNotEnabled_thenShouldNotSendAnyIndication() =
        runTest(TestKaliumDispatcher.default) {
            val (arrangement, typingIndicatorRepository) = Arrangement()
                .withTypingIndicatorStatus(false)
                .withSenderHandlerCall()
                .arrange()

            typingIndicatorRepository.sendTypingIndicatorStatus(conversationOne, Conversation.TypingIndicatorMode.STARTED)

            verify(arrangement.userPropertyRepository)
                .suspendFunction(arrangement.userPropertyRepository::getTypingIndicatorStatus)
                .wasInvoked()

            verify(arrangement.typingIndicatorSenderHandler)
                .function(arrangement.typingIndicatorSenderHandler::sendStartedAndEnqueueStoppingEvent)
                .with(any())
                .wasNotInvoked()
        }

    @Test
    fun givenAStartedTypingEvent_whenUserConfigIsEnabled_thenShouldSendAnyIndication() =
        runTest(TestKaliumDispatcher.default) {
            val (arrangement, typingIndicatorRepository) = Arrangement()
                .withTypingIndicatorStatus(true)
                .withSenderHandlerCall()
                .arrange()

            typingIndicatorRepository.sendTypingIndicatorStatus(conversationOne, Conversation.TypingIndicatorMode.STARTED)

            verify(arrangement.userPropertyRepository)
                .suspendFunction(arrangement.userPropertyRepository::getTypingIndicatorStatus)
                .wasInvoked()

            verify(arrangement.typingIndicatorSenderHandler)
                .function(arrangement.typingIndicatorSenderHandler::sendStartedAndEnqueueStoppingEvent)
                .with(any())
                .wasInvoked()
        }

    private class Arrangement {
        @Mock
        val userPropertyRepository: UserPropertyRepository = mock(UserPropertyRepository::class)

        @Mock
        val typingIndicatorSenderHandler: TypingIndicatorSenderHandler = mock(TypingIndicatorSenderHandler::class)

        fun withTypingIndicatorStatus(enabled: Boolean = true) = apply {
            given(userPropertyRepository)
                .suspendFunction(userPropertyRepository::getTypingIndicatorStatus)
                .whenInvoked()
                .thenReturn(enabled)
        }

        fun withSenderHandlerCall() = apply {
            given(typingIndicatorSenderHandler)
                .function(typingIndicatorSenderHandler::sendStartedAndEnqueueStoppingEvent)
                .whenInvokedWith(any())
                .thenReturn(Unit)
        }

        fun arrange() = this to TypingIndicatorOutgoingRepositoryImpl(
            userPropertyRepository = userPropertyRepository,
            typingIndicatorSenderHandler = typingIndicatorSenderHandler
        )
    }

    private companion object {
        val conversationOne = TestConversation.ID
    }
}
