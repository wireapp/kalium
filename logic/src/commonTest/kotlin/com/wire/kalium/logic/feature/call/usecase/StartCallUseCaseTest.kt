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

import com.wire.kalium.calling.ConversationTypeCalling
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.MockConversation
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.CallType
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.framework.TestCall
import com.wire.kalium.logic.sync.SyncStateObserver
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import com.wire.kalium.util.KaliumDispatcher
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class StartCallUseCaseTest {

    private fun TestScope.testDispatcher(): KaliumDispatcher = StandardTestDispatcher(testScheduler).testKaliumDispatcher()

    @Test
    fun givenAnIncomingCall_whenStartingANewCall_thenAnswerCallUseCaseShouldBeInvokedOnce() =
        runTest {
            val conversationId = MockConversation.ID

            val (arrangement, startCall) = Arrangement(testDispatcher())
                .withWaitingForSyncSucceeding()
                .withAnIncomingCall()
                .withCallConversationTypeUseCaseReturning(ConversationTypeCalling.Conference)
                .arrange()

            startCall.invoke(conversationId)

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.answerCall.invoke(conversationId)
            }

            verifySuspend(VerifyMode.not) {
                arrangement.callManager.startCall(any(), any(), any(), any(), any())
            }
        }

    @Test
    fun givenCallingParamsAndSyncSucceeds_whenRunningUseCase_thenInvokeStartCallOnce() = runTest {
        val conversationId = MockConversation.ID

        val (arrangement, startCall) = Arrangement(testDispatcher())
            .withWaitingForSyncSucceeding()
            .withNoIncomingCall()
            .withCallConversationTypeUseCaseReturning(ConversationTypeCalling.Conference)
            .arrange()

        startCall.invoke(conversationId, CallType.AUDIO)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.callManager.startCall(conversationId, CallType.AUDIO, any(), false, false)
        }

        verifySuspend(VerifyMode.not) {
            arrangement.answerCall.invoke(any())
        }
    }

    @Test
    fun givenCallingParamsAndSyncSucceeds_whenRunningUseCase_thenReturnSuccess() = runTest {
        val conversationId = MockConversation.ID

        val (arrangement, startCall) = Arrangement(testDispatcher())
            .withWaitingForSyncSucceeding()
            .withNoIncomingCall()
            .withCallConversationTypeUseCaseReturning(ConversationTypeCalling.Conference)
            .arrange()

        val result = startCall.invoke(conversationId, CallType.AUDIO)

        assertIs<StartCallUseCase.Result.Success>(result)
        verifySuspend(VerifyMode.not) {
            arrangement.answerCall.invoke(any())
        }
    }

    @Test
    fun givenCallingParamsAndSyncFails_whenRunningUseCase_thenStartCallIsNotInvoked() = runTest {
        val conversationId = MockConversation.ID

        val (arrangement, startCall) = Arrangement(testDispatcher())
            .withWaitingForSyncFailing()
            .withCallConversationTypeUseCaseReturning(ConversationTypeCalling.OneOnOne)
            .arrange()

        startCall.invoke(conversationId, CallType.AUDIO)

        verifySuspend(VerifyMode.not) {
            arrangement.callManager.startCall(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun givenCallingParamsAndSyncFails_whenRunningUseCase_thenShouldReturnSyncFailure() = runTest {
        val conversationId = MockConversation.ID

        val (_, startCall) = Arrangement(testDispatcher())
            .withWaitingForSyncFailing()
            .arrange()

        val result = startCall.invoke(conversationId, CallType.AUDIO)

        assertIs<StartCallUseCase.Result.SyncFailure>(result)
    }

    @Test
    fun givenMeetingConversation_whenRunningUseCase_thenStartCallReceivesMeetingFlag() = runTest {
        val conversationId = MockConversation.ID

        val (arrangement, startCall) = Arrangement(testDispatcher())
            .withWaitingForSyncSucceeding()
            .withNoIncomingCall()
            .withCallConversationTypeUseCaseReturning(ConversationTypeCalling.ConferenceMls, isMeeting = true)
            .arrange()

        startCall.invoke(conversationId, CallType.AUDIO)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.callManager.startCall(conversationId, CallType.AUDIO, any(), false, true)
        }
        verifySuspend(VerifyMode.not) {
            arrangement.answerCall.invoke(any())
        }
    }

    @Test
    fun givenCbrEnabled_WhenStartingACall_thenStartTheCallOnCBR() = runTest {
        val conversationId = MockConversation.ID

        val (arrangement, startCall) = Arrangement(testDispatcher())
            .withWaitingForSyncSucceeding()
            .withNoIncomingCall()
            .withCallConversationTypeUseCaseReturning(ConversationTypeCalling.Conference)
            .arrangeWithCBR()

        startCall.invoke(conversationId, CallType.AUDIO)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.callManager.startCall(conversationId, CallType.AUDIO, any(), true, false)
        }
        verifySuspend(VerifyMode.not) {
            arrangement.answerCall.invoke(any())
        }
    }

    private class Arrangement(dispatcher: KaliumDispatcher) {
        val callManager = mock<CallManager>(mode = MockMode.autoUnit)
        val syncStateObserver = mock<SyncStateObserver>(mode = MockMode.autoUnit)
        val answerCall = mock<AnswerCallUseCase>(mode = MockMode.autoUnit)
        val getCallConversationType = mock<GetCallConversationTypeProvider>(mode = MockMode.autoUnit)
        val callRepository = mock<CallRepository>(mode = MockMode.autoUnit)

        private val kaliumConfigs = KaliumConfigs()

        private val startCallUseCase = StartCallUseCase(
            lazy { callManager },
            syncStateObserver,
            kaliumConfigs,
            callRepository,
            getCallConversationType,
            answerCall,
            dispatcher
        )

        private val startCallUseCaseWithCBR = StartCallUseCase(
            lazy { callManager },
            syncStateObserver,
            KaliumConfigs(forceConstantBitrateCalls = true),
            callRepository,
            getCallConversationType,
            answerCall,
            dispatcher
        )

        fun withWaitingForSyncSucceeding() = withSyncReturning(Either.Right(Unit))
        fun withAnIncomingCall() = apply {
            everySuspend {
                callRepository.incomingCallsFlow()
            } returns (flowOf(listOf(TestCall.groupIncomingCall(MockConversation.ID))))
        }

        fun withCallConversationTypeUseCaseReturning(type: ConversationTypeCalling, isMeeting: Boolean = false) = apply {
            everySuspend {
                getCallConversationType.invoke(any())
            } returns (GetCallConversationTypeProvider.Result(type, isMeeting))
        }

        fun withNoIncomingCall() = apply {
            everySuspend {
                callRepository.incomingCallsFlow()
            } returns (flowOf(listOf()))
        }

        fun withWaitingForSyncFailing() =
            withSyncReturning(Either.Left(NetworkFailure.NoNetworkConnection(null)))

        private fun withSyncReturning(result: Either<CoreFailure, Unit>) = apply {
            everySuspend {
                syncStateObserver.waitUntilLiveOrFailure()
            } returns (result)
        }

        fun arrange() = this to startCallUseCase
        fun arrangeWithCBR() = this to startCallUseCaseWithCBR

    }
}
