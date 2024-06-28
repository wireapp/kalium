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
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.CallStatus
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.doesNothing
import io.mockative.eq
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class EndCallUseCaseTest {

    @Mock
    private val callManager = mock(CallManager::class)

    @Mock
    private val callRepository = mock(CallRepository::class)

    private lateinit var endCall: EndCallUseCase

    @BeforeTest
    fun setup() = runBlocking {
        endCall = EndCallUseCaseImpl(lazy { callManager }, callRepository, TestKaliumDispatcher)

        coEvery {
            callManager.endCall(eq(conversationId))
        }.returns(Unit)

        every { callRepository.updateIsCameraOnById(eq(conversationId), eq(false)) }
            .doesNothing()
    }

    @Test
    fun givenAnEstablishedCall_whenEndCallIsInvoked_thenUpdateStatusAndInvokeEndCallOnce() = runTest(TestKaliumDispatcher.main) {
        coEvery {
            callRepository.callsFlow()
        }.returns(flowOf(listOf(call)))

        endCall.invoke(conversationId)

        coVerify {
            callManager.endCall(eq(conversationId))
        }.wasInvoked(once)

        verify {
            callRepository.updateIsCameraOnById(eq(conversationId), eq(false))
        }.wasInvoked(once)

        coVerify {
            callRepository.updateCallStatusById(eq(conversationId), eq(CallStatus.CLOSED))
        }.wasInvoked(once)
    }

    @Test
    fun givenStillOngoingCall_whenEndCallIsInvoked_thenUpdateStatusAndInvokeEndCallOnce() = runTest(TestKaliumDispatcher.main) {
        val stillOngoingCall = call.copy(
            status = CallStatus.STILL_ONGOING,
            conversationType = Conversation.Type.GROUP
        )

        coEvery {
            callRepository.callsFlow()
        }.returns(flowOf(listOf(stillOngoingCall)))

        endCall.invoke(conversationId)

        coVerify {
            callManager.endCall(eq(conversationId))
        }.wasInvoked(once)

        verify {
            callRepository.updateIsCameraOnById(eq(conversationId), eq(false))
        }.wasInvoked(once)

        coVerify {
            callRepository.updateCallStatusById(eq(conversationId), eq(CallStatus.CLOSED_INTERNALLY))
        }.wasInvoked(once)
    }

    @Test
    fun givenStartedOutgoingCall_whenEndCallIsInvoked_thenUpdateStatusAndInvokeEndCallOnce() = runTest(TestKaliumDispatcher.main) {
        val stillOngoingCall = call.copy(
            status = CallStatus.STARTED,
            conversationType = Conversation.Type.GROUP
        )

        coEvery {
            callRepository.callsFlow()
        }.returns(flowOf(listOf(stillOngoingCall)))

        endCall.invoke(conversationId)

        coVerify {
            callManager.endCall(eq(conversationId))
        }.wasInvoked(once)

        verify {
            callRepository.updateIsCameraOnById(eq(conversationId), eq(false))
        }.wasInvoked(once)

        coVerify {
            callRepository.updateCallStatusById(eq(conversationId), eq(CallStatus.CLOSED_INTERNALLY))
        }.wasInvoked(once)
    }

    @Test
    fun givenIncomingCall_whenEndCallIsInvoked_thenUpdateStatusAndInvokeEndCallOnce() = runTest(TestKaliumDispatcher.main) {
        val stillOngoingCall = call.copy(
            status = CallStatus.INCOMING,
            conversationType = Conversation.Type.GROUP
        )

        coEvery {
            callRepository.callsFlow()
        }.returns(flowOf(listOf(stillOngoingCall)))

        endCall.invoke(conversationId)

        coVerify {
            callManager.endCall(eq(conversationId))
        }.wasInvoked(once)

        verify {
            callRepository.updateIsCameraOnById(eq(conversationId), eq(false))
        }.wasInvoked(once)

        coVerify {
            callRepository.updateCallStatusById(eq(conversationId), eq(CallStatus.CLOSED_INTERNALLY))
        }.wasInvoked(once)
    }

    @Test
    fun givenNoValidCalls_whenEndCallIsInvoked_thenDoNotUpdateStatus() = runTest(TestKaliumDispatcher.main) {
        val closedCall = call.copy(
            status = CallStatus.CLOSED
        )

        coEvery {
            callRepository.callsFlow()
        }.returns(flowOf(listOf(closedCall)))

        endCall.invoke(conversationId)

        coVerify {
            callManager.endCall(eq(conversationId))
        }.wasInvoked(once)

        verify {
            callRepository.updateIsCameraOnById(eq(conversationId), eq(false))
        }.wasInvoked(once)

        coVerify {
            callRepository.updateCallStatusById(any(), any())
        }.wasNotInvoked()
    }

    companion object {
        private val conversationId = ConversationId("someone", "wire.com")
        private val call = Call(
            conversationId = conversationId,
            status = CallStatus.ESTABLISHED,
            callerId = "called-id",
            isMuted = false,
            isCameraOn = false,
            isCbrEnabled = false,
            conversationName = null,
            conversationType = Conversation.Type.ONE_ON_ONE,
            callerName = null,
            callerTeamName = null,
            establishedTime = null
        )
    }
}
