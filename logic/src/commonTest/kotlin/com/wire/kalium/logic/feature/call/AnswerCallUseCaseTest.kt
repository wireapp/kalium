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

package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.usecase.AnswerCallUseCase
import com.wire.kalium.logic.feature.call.usecase.AnswerCallUseCaseImpl
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.thenDoNothing
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class AnswerCallUseCaseTest {

    @Mock
    private val callManager = mock(classOf<CallManager>())

    private lateinit var answerCallUseCase: AnswerCallUseCase

    @BeforeTest
    fun setUp() {
        answerCallUseCase = AnswerCallUseCaseImpl(
            callManager = lazy { callManager }
        )
    }

    @Test
    fun givenAConversationId_whenAnsweringACallOfThatConversation_thenCallManagerIsCalledWithTheCorrectId() = runTest {
        val conversationId = ConversationId(
            value = "value1",
            domain = "domain1"
        )

        given(callManager)
            .suspendFunction(callManager::answerCall)
            .whenInvokedWith(eq(conversationId))
            .thenDoNothing()

        answerCallUseCase.invoke(
            conversationId = conversationId
        )

        verify(callManager)
            .suspendFunction(callManager::answerCall)
            .with(eq(conversationId))
            .wasInvoked(exactly = once)
    }
}
