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
package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.TypingIndicatorOutgoingRepository
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SendTypingEventUseCaseTest {

    @Test
    fun givenATypingEvent_whenCallingSendSucceed_thenReturnSuccess() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withTypingIndicatorStatusAndResult(Conversation.TypingIndicatorMode.STOPPED)
            .arrange()

        useCase(TestConversation.ID, Conversation.TypingIndicatorMode.STOPPED)

        verify(arrangement.typingIndicatorRepository)
            .suspendFunction(arrangement.typingIndicatorRepository::sendTypingIndicatorStatus)
            .with(eq(TestConversation.ID), eq(Conversation.TypingIndicatorMode.STOPPED))
            .wasInvoked()
    }

    @Test
    fun givenATypingEvent_whenCallingSendFails_thenReturnIgnoringFailure() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withTypingIndicatorStatusAndResult(
                Conversation.TypingIndicatorMode.STARTED,
                Either.Left(CoreFailure.Unknown(RuntimeException("Some error")))
            )
            .arrange()

        val result = useCase(TestConversation.ID, Conversation.TypingIndicatorMode.STARTED)

        verify(arrangement.typingIndicatorRepository)
            .suspendFunction(arrangement.typingIndicatorRepository::sendTypingIndicatorStatus)
            .with(eq(TestConversation.ID), eq(Conversation.TypingIndicatorMode.STARTED))
            .wasInvoked()
        assertEquals(Unit, result)
    }

    private class Arrangement {
        @Mock
        val typingIndicatorRepository: TypingIndicatorOutgoingRepository = mock(TypingIndicatorOutgoingRepository::class)

        fun withTypingIndicatorStatusAndResult(
            typingMode: Conversation.TypingIndicatorMode,
            result: Either<CoreFailure, Unit> = Either.Right(Unit)
        ) = apply {
            given(typingIndicatorRepository)
                .suspendFunction(typingIndicatorRepository::sendTypingIndicatorStatus)
                .whenInvokedWith(any(), eq(typingMode))
                .thenReturn(result)
        }

        fun arrange() = this to SendTypingEventUseCaseImpl(
            typingIndicatorRepository
        )
    }
}
