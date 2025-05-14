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

import com.wire.kalium.logic.data.call.Call
import com.wire.kalium.logic.data.call.CallStatus
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.framework.TestCall
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class AnswerCallUseCaseTest {

    private val observeOngoingAndIncomingCalls = mock(ObserveOngoingAndIncomingCallsUseCase::class)
    private val muteCall = mock(MuteCallUseCase::class)
    private val callManager = mock(CallManager::class)

    private val answerCall = AnswerCallUseCaseImpl(
        observeOngoingAndIncomingCalls = observeOngoingAndIncomingCalls,
        muteCall = muteCall,
        callManager = lazy { callManager },
        kaliumConfigs = KaliumConfigs(),
        dispatchers = TestKaliumDispatcher
    )

    @Test
    fun givenIncomingVideoCallWithCbrEnabled_whenAnsweringACall_thenInvokeAnswerCallWithCbrOnce() = runTest(TestKaliumDispatcher.main) {
        val isCbrEnabled = true
        val configs = KaliumConfigs(forceConstantBitrateCalls = isCbrEnabled)

        coEvery {
            observeOngoingAndIncomingCalls()
        }.returns(flowOf(listOf(call.copy(isCameraOn = true))))

        coEvery {
            callManager.answerCall(eq(conversationId), eq(configs.forceConstantBitrateCalls), any())
        }.returns(Unit)

        val answerCallWithCBR = AnswerCallUseCaseImpl(
            observeOngoingAndIncomingCalls = observeOngoingAndIncomingCalls,
            muteCall = muteCall,
            callManager = lazy { callManager },
            kaliumConfigs = configs,
            dispatchers = testKaliumDispatcher
        )

        answerCallWithCBR(
            conversationId = conversationId
        )

        coVerify {
            callManager.answerCall(eq(conversationId), eq(isCbrEnabled), eq(true))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenAnOnGoingGroupCall_whenJoiningIt_thenMuteThatCall() = runTest(TestKaliumDispatcher.main) {
        coEvery {
            observeOngoingAndIncomingCalls()
        }.returns(flowOf(listOf(call.copy(status = CallStatus.STILL_ONGOING))))

        answerCall(
            conversationId = conversationId
        )

        coVerify {
            callManager.answerCall(eq(conversationId), eq(false), eq(false))
        }.wasInvoked(exactly = once)

        coVerify {
            muteCall(any(), any())
        }.wasInvoked(exactly = once)

    }

    companion object {
        val conversationId = ConversationId(
            value = "value1",
            domain = "domain1"
        )
        val call = Call(
            conversationId = conversationId,
            status = CallStatus.INCOMING,
            isMuted = true,
            isCameraOn = false,
            isCbrEnabled = false,
            callerId = TestCall.CALLER_ID,
            conversationName = "caller-name",
            conversationType = Conversation.Type.Group.Regular,
            callerName = "Name",
            callerTeamName = "group",
            establishedTime = null
        )
    }
}
