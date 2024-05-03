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
package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.every
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

            coVerify {
                arrangement.userPropertyRepository.getTypingIndicatorStatus()
            }.wasInvoked()

            verify {
                arrangement.typingIndicatorSenderHandler.sendStartedAndEnqueueStoppingEvent(any())
            }.wasNotInvoked()
        }

    @Test
    fun givenAStartedTypingEvent_whenUserConfigIsEnabled_thenShouldSendAnyIndication() =
        runTest(TestKaliumDispatcher.default) {
            val (arrangement, typingIndicatorRepository) = Arrangement()
                .withTypingIndicatorStatus(true)
                .withSenderHandlerCall()
                .arrange()

            typingIndicatorRepository.sendTypingIndicatorStatus(conversationOne, Conversation.TypingIndicatorMode.STARTED)

            coVerify {
                arrangement.userPropertyRepository.getTypingIndicatorStatus()
            }.wasInvoked()

            verify {
                arrangement.typingIndicatorSenderHandler.sendStartedAndEnqueueStoppingEvent(any())
            }.wasInvoked()
        }

    @Test
    fun givenStoppedTypingEvent_whenCalled_thenShouldDelegateCallToHandler() =
        runTest(TestKaliumDispatcher.default) {
            val (arrangement, typingIndicatorRepository) = Arrangement()
                .withTypingIndicatorStatus(true)
                .withSenderHandlerStoppedCall()
                .arrange()

            typingIndicatorRepository.sendTypingIndicatorStatus(conversationOne, Conversation.TypingIndicatorMode.STOPPED)

            coVerify {
                arrangement.userPropertyRepository.getTypingIndicatorStatus()
            }.wasInvoked()

            verify {
                arrangement.typingIndicatorSenderHandler.sendStoppingEvent(any())
            }.wasInvoked()
        }

    private class Arrangement {
        @Mock
        val userPropertyRepository: UserPropertyRepository = mock(UserPropertyRepository::class)

        @Mock
        val typingIndicatorSenderHandler: TypingIndicatorSenderHandler = mock(TypingIndicatorSenderHandler::class)

        suspend fun withTypingIndicatorStatus(enabled: Boolean = true) = apply {
            coEvery {
                userPropertyRepository.getTypingIndicatorStatus()
            }.returns(enabled)
        }

        fun withSenderHandlerStoppedCall() = apply {
            every {
                typingIndicatorSenderHandler.sendStoppingEvent(any())
            }.returns(Unit)
        }

        fun withSenderHandlerCall() = apply {
            every {
                typingIndicatorSenderHandler.sendStartedAndEnqueueStoppingEvent(any())
            }.returns(Unit)
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
