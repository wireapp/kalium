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

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.Call
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.feature.call.CallStatus
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.util.KaliumConfigStub
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.thenDoNothing
import io.mockative.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class AnswerCallUseCaseTest {

    @Mock
    private val getAllCallsWithSortedParticipants = mock(classOf<GetAllCallsWithSortedParticipantsUseCase>())

    @Mock
    private val muteCall = mock(classOf<MuteCallUseCase>())

    @Mock
    private val unMuteCall = mock(classOf<UnMuteCallUseCase>())

    @Mock
    private val callManager = mock(classOf<CallManager>())

    private val answerCall = AnswerCallUseCaseImpl(
        allCalls = getAllCallsWithSortedParticipants,
        muteCall = muteCall,
        unMuteCall = unMuteCall,
        callManager = lazy { callManager },
        kaliumConfigs = KaliumConfigStub()
    )

    @BeforeTest
    fun setUp() {
        given(callManager)
            .suspendFunction(callManager::answerCall)
            .whenInvokedWith(eq(conversationId), eq(false))
            .thenDoNothing()
    }

    @Test
    fun givenCbrEnabled_whenAnsweringACall_thenInvokeAnswerCallWithCbrOnce() = runTest {
        val isCbrEnabled = true
        val configs = KaliumConfigStub(forceConstantBitrateCalls = isCbrEnabled)

        given(getAllCallsWithSortedParticipants).coroutine { invoke() }
            .then { flowOf(listOf()) }

        val answerCallWithCBR = AnswerCallUseCaseImpl(
            allCalls = getAllCallsWithSortedParticipants,
            muteCall = muteCall,
            unMuteCall = unMuteCall,
            callManager = lazy { callManager },
            kaliumConfigs = configs
        )

        given(callManager)
            .suspendFunction(callManager::answerCall)
            .whenInvokedWith(eq(conversationId), eq(configs.forceConstantBitrateCalls))
            .thenDoNothing()

        answerCallWithCBR(
            conversationId = conversationId
        )

        verify(callManager)
            .suspendFunction(callManager::answerCall)
            .with(eq(conversationId), eq(isCbrEnabled))
            .wasInvoked(exactly = once)
    }


    @Test
    fun givenACall_whenAnsweringIt_thenInvokeAnswerCallOnce() = runTest {
        given(getAllCallsWithSortedParticipants).coroutine { invoke() }
            .then { flowOf(listOf()) }

        answerCall(
            conversationId = conversationId
        )

        verify(callManager)
            .suspendFunction(callManager::answerCall)
            .with(eq(conversationId), eq(false))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenOnGoingGroupCall_whenJoiningIt_thenMuteThatCall() = runTest {
        given(getAllCallsWithSortedParticipants).coroutine { invoke() }
            .then { flowOf(listOf(call)) }

        answerCall(
            conversationId = conversationId
        )

        verify(muteCall)
            .suspendFunction(muteCall::invoke)
            .with(eq(conversationId), eq(true))
            .wasInvoked(exactly = once)

        verify(callManager)
            .suspendFunction(callManager::answerCall)
            .with(eq(conversationId), eq(false))
            .wasInvoked(exactly = once)

    }

    @Test
    fun givenIncomingOneOnOneCallWithIsMutedFalse_whenAnsweringTheCall_thenUnMuteThatCall() = runTest {
        val newCall = call.copy(
            status = CallStatus.INCOMING,
            conversationType = Conversation.Type.ONE_ON_ONE,
            isMuted = false
        )

        given(getAllCallsWithSortedParticipants).coroutine { invoke() }
            .then { flowOf(listOf(newCall)) }

        answerCall(
            conversationId = conversationId
        )

        verify(unMuteCall)
            .suspendFunction(unMuteCall::invoke)
            .with(eq(conversationId), eq(true))
            .wasInvoked(exactly = once)

        verify(callManager)
            .suspendFunction(callManager::answerCall)
            .with(eq(conversationId), eq(false))
            .wasInvoked(exactly = once)
    }

    companion object {
        val conversationId = ConversationId(
            value = "value1",
            domain = "domain1"
        )
        val call = Call(
            conversationId = conversationId,
            status = CallStatus.STILL_ONGOING,
            isMuted = true,
            isCameraOn = false,
            isCbrEnabled = false,
            callerId = "id",
            conversationName = "caller-name",
            conversationType = Conversation.Type.GROUP,
            callerName = "Name",
            callerTeamName = "group",
            establishedTime = null
        )
    }
}
