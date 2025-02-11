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
import io.mockative.Mock
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class AnswerCallUseCaseTest {

    @Mock
    private val getIncomingCalls = mock(GetIncomingCallsUseCase::class)

    @Mock
    private val muteCall = mock(MuteCallUseCase::class)

    @Mock
    private val unMuteCall = mock(UnMuteCallUseCase::class)

    @Mock
    private val callManager = mock(CallManager::class)

    private val answerCall = AnswerCallUseCaseImpl(
        incomingCalls = getIncomingCalls,
        muteCall = muteCall,
        unMuteCall = unMuteCall,
        callManager = lazy { callManager },
        kaliumConfigs = KaliumConfigs(),
        dispatchers = TestKaliumDispatcher
    )

    @BeforeTest
    fun setUp() = runBlocking {
        coEvery {
            callManager.answerCall(eq(conversationId), eq(false))
        }.returns(Unit)
    }

    @Test
    fun givenCbrEnabled_whenAnsweringACall_thenInvokeAnswerCallWithCbrOnce() = runTest(TestKaliumDispatcher.main) {
        val isCbrEnabled = true
        val configs = KaliumConfigs(forceConstantBitrateCalls = isCbrEnabled)

        coEvery {
            getIncomingCalls()
        }.returns(flowOf(listOf(call)))

        val answerCallWithCBR = AnswerCallUseCaseImpl(
            incomingCalls = getIncomingCalls,
            muteCall = muteCall,
            unMuteCall = unMuteCall,
            callManager = lazy { callManager },
            kaliumConfigs = configs,
            dispatchers = testKaliumDispatcher
        )

        coEvery {
            callManager.answerCall(eq(conversationId), eq(configs.forceConstantBitrateCalls))
        }.returns(Unit)

        answerCallWithCBR(
            conversationId = conversationId
        )

        coVerify {
            callManager.answerCall(eq(conversationId), eq(isCbrEnabled))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenAIncomingCall_whenAnsweringIt_thenInvokeAnswerCallOnce() = runTest(TestKaliumDispatcher.main) {
        coEvery {
            getIncomingCalls()
        }.returns(flowOf(listOf(call)))

        answerCall(
            conversationId = conversationId
        )

        coVerify {
            callManager.answerCall(eq(conversationId), eq(false))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenOnGoingGroupCall_whenJoiningIt_thenMuteThatCall() = runTest(TestKaliumDispatcher.main) {
        coEvery {
            getIncomingCalls()
        }.returns(flowOf(listOf(call.copy(
            status = CallStatus.STILL_ONGOING
        ))))

        answerCall(
            conversationId = conversationId
        )

        coVerify {
            muteCall.invoke(eq(conversationId), eq(true))
        }.wasInvoked(exactly = once)

        coVerify {
            callManager.answerCall(eq(conversationId), eq(false))
        }.wasInvoked(exactly = once)

    }

    @Test
    fun givenIncomingOneOnOneCallWithIsMutedFalse_whenAnsweringTheCall_thenUnMuteThatCall() = runTest(TestKaliumDispatcher.main) {
        val newCall = call.copy(
            conversationType = Conversation.Type.ONE_ON_ONE,
            isMuted = false
        )

        coEvery {
            getIncomingCalls()
        }.returns(flowOf(listOf(newCall)))

        answerCall(
            conversationId = conversationId
        )

        coVerify {
            unMuteCall.invoke(eq(conversationId), eq(true))
        }.wasInvoked(exactly = once)

        coVerify {
            callManager.answerCall(eq(conversationId), eq(false))
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
            conversationType = Conversation.Type.GROUP,
            callerName = "Name",
            callerTeamName = "group",
            establishedTime = null
        )
    }
}
