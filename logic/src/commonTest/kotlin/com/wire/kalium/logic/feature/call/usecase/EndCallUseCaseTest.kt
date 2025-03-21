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
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.user.ShouldAskCallFeedbackUseCase
import com.wire.kalium.logic.feature.user.ShouldAskCallFeedbackUseCaseResult
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.util.arrangement.repository.CallManagerArrangement
import com.wire.kalium.logic.util.arrangement.repository.CallManagerArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.CallRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.CallRepositoryArrangementImpl
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.doesNothing
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class EndCallUseCaseTest {

    @Test
    fun givenAnEstablishedCall_whenEndCallIsInvoked_thenUpdateStatusAndInvokeEndCallOnce() = runTest(TestKaliumDispatcher.main) {

        val (arrangement, endCall) = Arrangement().arrange {
            withEndCall()
            withCallsFlow(flowOf(listOf(call)))
            withUpdateIsCameraOnById()
        }

        endCall.invoke(conversationId)

        coVerify {
            arrangement.callManager.endCall(eq(conversationId))
        }.wasInvoked(once)

        verify {
            arrangement.callRepository.updateIsCameraOnById(eq(conversationId), eq(false))
        }.wasInvoked(once)

        coVerify {
            arrangement.callRepository.updateCallStatusById(eq(conversationId), eq(CallStatus.CLOSED))
        }.wasInvoked(once)

        coVerify {
            arrangement.endCallResultListener.onCallEndedAskForFeedback(
                eq(ShouldAskCallFeedbackUseCaseResult.ShouldAskCallFeedback(100))
            )
        }.wasInvoked(once)
    }

    @Test
    fun givenStillOngoingCall_whenEndCallIsInvoked_thenUpdateStatusAndInvokeEndCallOnce() = runTest(TestKaliumDispatcher.main) {
        val stillOngoingCall = call.copy(
            status = CallStatus.STILL_ONGOING,
            conversationType = Conversation.Type.Group.Regular
        )
        val (arrangement, endCall) = Arrangement().arrange {
            withEndCall()
            withCallsFlow(flowOf(listOf(stillOngoingCall)))
            withUpdateIsCameraOnById()
        }

        endCall.invoke(conversationId)

        coVerify {
            arrangement.callManager.endCall(eq(conversationId))
        }.wasInvoked(once)

        verify {
            arrangement.callRepository.updateIsCameraOnById(eq(conversationId), eq(false))
        }.wasInvoked(once)

        coVerify {
            arrangement.callRepository.updateCallStatusById(eq(conversationId), eq(CallStatus.CLOSED_INTERNALLY))
        }.wasInvoked(once)

        coVerify {
            arrangement.endCallResultListener.onCallEndedAskForFeedback(
                eq(ShouldAskCallFeedbackUseCaseResult.ShouldAskCallFeedback(100))
            )
        }.wasInvoked(once)
    }

    @Test
    fun givenStartedOutgoingCall_whenEndCallIsInvoked_thenUpdateStatusAndInvokeEndCallOnce() = runTest(TestKaliumDispatcher.main) {
        val stillOngoingCall = call.copy(
            status = CallStatus.STARTED,
            conversationType = Conversation.Type.Group.Regular
        )
        val (arrangement, endCall) = Arrangement().arrange {
            withEndCall()
            withCallsFlow(flowOf(listOf(stillOngoingCall)))
            withUpdateIsCameraOnById()
        }

        endCall.invoke(conversationId)

        coVerify {
            arrangement.callManager.endCall(eq(conversationId))
        }.wasInvoked(once)

        verify {
            arrangement.callRepository.updateIsCameraOnById(eq(conversationId), eq(false))
        }.wasInvoked(once)

        coVerify {
            arrangement.callRepository.updateCallStatusById(eq(conversationId), eq(CallStatus.CLOSED_INTERNALLY))
        }.wasInvoked(once)

        coVerify {
            arrangement.endCallResultListener.onCallEndedAskForFeedback(
                eq(ShouldAskCallFeedbackUseCaseResult.ShouldAskCallFeedback(100))
            )
        }.wasInvoked(once)
    }

    @Test
    fun givenIncomingCall_whenEndCallIsInvoked_thenUpdateStatusAndInvokeEndCallOnce() = runTest(TestKaliumDispatcher.main) {
        val stillOngoingCall = call.copy(
            status = CallStatus.INCOMING,
            conversationType = Conversation.Type.Group.Regular
        )
        val (arrangement, endCall) = Arrangement().arrange {
            withEndCall()
            withCallsFlow(flowOf(listOf(stillOngoingCall)))
            withUpdateIsCameraOnById()
        }

        endCall.invoke(conversationId)

        coVerify {
            arrangement.callManager.endCall(eq(conversationId))
        }.wasInvoked(once)

        verify {
            arrangement.callRepository.updateIsCameraOnById(eq(conversationId), eq(false))
        }.wasInvoked(once)

        coVerify {
            arrangement.callRepository.updateCallStatusById(eq(conversationId), eq(CallStatus.CLOSED_INTERNALLY))
        }.wasInvoked(once)

        coVerify {
            arrangement.endCallResultListener.onCallEndedAskForFeedback(
                eq(ShouldAskCallFeedbackUseCaseResult.ShouldAskCallFeedback(100))
            )
        }.wasInvoked(once)
    }

    @Test
    fun givenNoValidCalls_whenEndCallIsInvoked_thenDoNotUpdateStatus() = runTest(TestKaliumDispatcher.main) {
        val closedCall = call.copy(
            status = CallStatus.CLOSED
        )
        val (arrangement, endCall) = Arrangement().arrange {
            withEndCall()
            withCallsFlow(flowOf(listOf(closedCall)))
            withUpdateIsCameraOnById()
        }

        endCall.invoke(conversationId)

        coVerify {
            arrangement.callManager.endCall(eq(conversationId))
        }.wasInvoked(once)

        verify {
            arrangement.callRepository.updateIsCameraOnById(eq(conversationId), eq(false))
        }.wasInvoked(once)

        coVerify {
            arrangement.callRepository.updateCallStatusById(any(), any())
        }.wasNotInvoked()

        coVerify {
            arrangement.endCallResultListener.onCallEndedAskForFeedback(
                eq(ShouldAskCallFeedbackUseCaseResult.ShouldAskCallFeedback(100))
            )
        }.wasInvoked(once)
    }

    private class Arrangement : CallRepositoryArrangement by CallRepositoryArrangementImpl(),
        CallManagerArrangement by CallManagerArrangementImpl() {

        @Mock
        val endCallResultListener = mock(EndCallResultListener::class)

        @Mock
        val shouldAskCallFeedback = mock(ShouldAskCallFeedbackUseCase::class)

        fun arrange(block: suspend Arrangement.() -> Unit): Pair<Arrangement, EndCallUseCase> {
            runBlocking {
                withShouldAskCallFeedback()
                withOnCallEndedAskForFeedback()
            }
            runBlocking { block() }
            return this to EndCallUseCaseImpl(
                lazy { callManager },
                callRepository,
                endCallResultListener,
                shouldAskCallFeedback,
                TestKaliumDispatcher
            )
        }

        suspend fun withShouldAskCallFeedback(
            result: ShouldAskCallFeedbackUseCaseResult = ShouldAskCallFeedbackUseCaseResult.ShouldAskCallFeedback(100)
        ) {
            coEvery { shouldAskCallFeedback.invoke(any(), any()) }.returns(result)
        }

        suspend fun withOnCallEndedAskForFeedback() {
            coEvery { endCallResultListener.onCallEndedAskForFeedback(any()) }.doesNothing()
        }
    }

    companion object {
        private val conversationId = ConversationId("someone", "wire.com")
        private val call = Call(
            conversationId = conversationId,
            status = CallStatus.ESTABLISHED,
            callerId = UserId("called-id", "domain"),
            isMuted = false,
            isCameraOn = false,
            isCbrEnabled = false,
            conversationName = null,
            conversationType = Conversation.Type.OneOnOne,
            callerName = null,
            callerTeamName = null,
            establishedTime = null
        )
    }
}
