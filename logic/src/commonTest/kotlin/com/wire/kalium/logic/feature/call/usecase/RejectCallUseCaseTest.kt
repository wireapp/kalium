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

package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.feature.call.CallStatus
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.thenDoNothing
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class RejectCallUseCaseTest {

    @Mock
    private val callManager = mock(classOf<CallManager>())

    @Mock
    private val callRepository = mock(classOf<CallRepository>())

    private lateinit var rejectCallUseCase: RejectCallUseCase

    @BeforeTest
    fun setup() {
        rejectCallUseCase = RejectCallUseCase(lazy{ callManager }, callRepository)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenCallingParams_whenRunningUseCase_thenInvokeRejectCallOnce() = runTest {
        val conversationId = ConversationId("someone", "wire.com")

        given(callManager)
            .suspendFunction(callManager::rejectCall)
            .whenInvokedWith(eq(conversationId))
            .thenDoNothing()

        given(callRepository)
            .suspendFunction(callRepository::updateCallStatusById)
            .whenInvokedWith(eq(conversationId), eq(CallStatus.REJECTED))
            .thenDoNothing()

        rejectCallUseCase.invoke(conversationId)

        verify(callManager)
            .suspendFunction(callManager::rejectCall)
            .with(eq(conversationId))
            .wasInvoked(once)

        verify(callRepository)
            .suspendFunction(callRepository::updateCallStatusById)
            .with(eq(conversationId), eq(CallStatus.REJECTED))
            .wasInvoked(once)
    }

}
