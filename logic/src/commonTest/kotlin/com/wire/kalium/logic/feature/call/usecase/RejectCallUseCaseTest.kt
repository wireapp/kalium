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

package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.CallStatus
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class RejectCallUseCaseTest {

        private val callManager = mock(CallManager::class)

        private val callRepository = mock(CallRepository::class)

    private lateinit var rejectCallUseCase: RejectCallUseCase

    @BeforeTest
    fun setup() {
        rejectCallUseCase = RejectCallUseCase(lazy { callManager }, callRepository, TestKaliumDispatcher)
    }

    @Test
    fun givenCallingParams_whenRunningUseCase_thenInvokeRejectCallOnce() = runTest(TestKaliumDispatcher.main) {
        val conversationId = ConversationId("someone", "wire.com")

        coEvery {
            callManager.rejectCall(eq(conversationId))
        }.returns(Unit)

        coEvery {
            callRepository.updateCallStatusById(eq(conversationId), eq(CallStatus.REJECTED))
        }.returns(Unit)

        rejectCallUseCase.invoke(conversationId)

        coVerify {
            callManager.rejectCall(eq(conversationId))
        }.wasInvoked(once)

        coVerify {
            callRepository.updateCallStatusById(eq(conversationId), eq(CallStatus.REJECTED))
        }.wasInvoked(once)
    }

}
