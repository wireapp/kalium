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
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class TypingIndicatorOutgoingRepositoryTest {

    @Test
    fun givenAStartedTypingEvent_whenUserConfigNotEnabled_thenShouldNotSendAnyIndication() =
        runTest(TestKaliumDispatcher.default) {
            val (arrangement, typingIndicatorRepository) = Arrangement()
                .withTypingIndicatorStatus(false)
                .withSenderHandlerCall()
                .arrange()

            typingIndicatorRepository.sendTypingIndicatorStatus(conversationOne, Conversation.TypingIndicatorMode.STARTED)

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.userPropertyRepository.getTypingIndicatorStatus()
            }

            verify(VerifyMode.exactly(0)) {
                arrangement.typingIndicatorSenderHandler.sendStartedAndEnqueueStoppingEvent(any())
            }
        }

    @Test
    fun givenAStartedTypingEvent_whenUserConfigIsEnabled_thenShouldSendAnyIndication() =
        runTest(TestKaliumDispatcher.default) {
            val (arrangement, typingIndicatorRepository) = Arrangement()
                .withTypingIndicatorStatus(true)
                .withSenderHandlerCall()
                .arrange()

            typingIndicatorRepository.sendTypingIndicatorStatus(conversationOne, Conversation.TypingIndicatorMode.STARTED)

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.userPropertyRepository.getTypingIndicatorStatus()
            }

            verify(VerifyMode.exactly(1)) {
                arrangement.typingIndicatorSenderHandler.sendStartedAndEnqueueStoppingEvent(any())
            }
        }

    @Test
    fun givenStoppedTypingEvent_whenCalled_thenShouldDelegateCallToHandler() =
        runTest(TestKaliumDispatcher.default) {
            val (arrangement, typingIndicatorRepository) = Arrangement()
                .withTypingIndicatorStatus(true)
                .withSenderHandlerStoppedCall()
                .arrange()

            typingIndicatorRepository.sendTypingIndicatorStatus(conversationOne, Conversation.TypingIndicatorMode.STOPPED)

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.userPropertyRepository.getTypingIndicatorStatus()
            }

            verify(VerifyMode.exactly(1)) {
                arrangement.typingIndicatorSenderHandler.sendStoppingEvent(any())
            }
        }

    private class Arrangement {
        val userPropertyRepository: UserPropertyRepository = mock<UserPropertyRepository>(mode = MockMode.autoUnit)
        val typingIndicatorSenderHandler: TypingIndicatorSenderHandler = mock<TypingIndicatorSenderHandler>(mode = MockMode.autoUnit)

        suspend fun withTypingIndicatorStatus(enabled: Boolean = true) = apply {
            everySuspend {
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
