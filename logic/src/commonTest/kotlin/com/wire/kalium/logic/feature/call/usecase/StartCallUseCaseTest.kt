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
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.CallType
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.framework.TestCall
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import com.wire.kalium.util.KaliumDispatcher
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class StartCallUseCaseTest {

    @Test
    fun givenAnIncomingCall_whenStartingANewCall_thenAnswerCallUseCaseShouldBeInvokedOnce() =
        runTest {
            val conversationId = TestConversation.ID

            val (arrangement, startCall) = Arrangement(testKaliumDispatcher)
                .withWaitingForSyncSucceeding()
                .withAnIncomingCall()
                .withCallConversationTypeUseCaseReturning(ConversationTypeCalling.Conference)
                .arrange()

            startCall.invoke(conversationId)

            coVerify {
                arrangement.answerCall.invoke(eq(conversationId))
            }.wasInvoked(once)

            coVerify {
                arrangement.callManager.startCall(any(), any(), any(), any())
            }.wasNotInvoked()
        }

    @Test
    fun givenCallingParamsAndSyncSucceeds_whenRunningUseCase_thenInvokeStartCallOnce() = runTest {
        val conversationId = TestConversation.ID

        val (arrangement, startCall) = Arrangement(testKaliumDispatcher)
            .withWaitingForSyncSucceeding()
            .withNoIncomingCall()
            .withCallConversationTypeUseCaseReturning(ConversationTypeCalling.Conference)
            .arrange()

        startCall.invoke(conversationId, CallType.AUDIO)

        coVerify {
            arrangement.callManager.startCall(eq(conversationId), eq(CallType.AUDIO), any(), eq(false))
        }.wasInvoked(once)

        coVerify {
            arrangement.answerCall.invoke(any())
        }.wasNotInvoked()
    }

    @Test
    fun givenCallingParamsAndSyncSucceeds_whenRunningUseCase_thenReturnSuccess() = runTest {
        val conversationId = TestConversation.ID

        val (arrangement, startCall) = Arrangement(testKaliumDispatcher)
            .withWaitingForSyncSucceeding()
            .withNoIncomingCall()
            .withCallConversationTypeUseCaseReturning(ConversationTypeCalling.Conference)
            .arrange()

        val result = startCall.invoke(conversationId, CallType.AUDIO)

        assertIs<StartCallUseCase.Result.Success>(result)
        coVerify {
            arrangement.answerCall.invoke(any())
        }.wasNotInvoked()
    }

    @Test
    fun givenCallingParamsAndSyncFails_whenRunningUseCase_thenStartCallIsNotInvoked() = runTest {
        val conversationId = TestConversation.ID

        val (arrangement, startCall) = Arrangement(testKaliumDispatcher)
            .withWaitingForSyncFailing()
            .withCallConversationTypeUseCaseReturning(ConversationTypeCalling.OneOnOne)
            .arrange()

        startCall.invoke(conversationId, CallType.AUDIO)

        coVerify {
            arrangement.callManager.startCall(any(), any(), any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenCallingParamsAndSyncFails_whenRunningUseCase_thenShouldReturnSyncFailure() = runTest {
        val conversationId = TestConversation.ID

        val (_, startCall) = Arrangement(testKaliumDispatcher)
            .withWaitingForSyncFailing()
            .arrange()

        val result = startCall.invoke(conversationId, CallType.AUDIO)

        assertIs<StartCallUseCase.Result.SyncFailure>(result)
    }

    @Test
    fun givenCbrEnabled_WhenStartingACall_thenStartTheCallOnCBR() = runTest {
        val conversationId = TestConversation.ID

        val (arrangement, startCall) = Arrangement(testKaliumDispatcher)
            .withWaitingForSyncSucceeding()
            .withNoIncomingCall()
            .withCallConversationTypeUseCaseReturning(ConversationTypeCalling.Conference)
            .arrangeWithCBR()

        startCall.invoke(conversationId, CallType.AUDIO)

        coVerify {
            arrangement.callManager.startCall(eq(conversationId), eq(CallType.AUDIO), any(), eq(true))
        }.wasInvoked(once)
        coVerify {
            arrangement.answerCall.invoke(any())
        }.wasNotInvoked()
    }

    private class Arrangement(private var dispatcher: KaliumDispatcher = TestKaliumDispatcher) {

        @Mock
        val callManager = mock(CallManager::class)

        @Mock
        val syncManager = mock(SyncManager::class)

        @Mock
        val answerCall = mock(AnswerCallUseCase::class)

        @Mock
        val getCallConversationType = mock(GetCallConversationTypeProvider::class)

        @Mock
        val callRepository = mock(CallRepository::class)

        private val kaliumConfigs = KaliumConfigs()

        private val startCallUseCase = StartCallUseCase(
            lazy { callManager },
            syncManager,
            kaliumConfigs,
            callRepository,
            getCallConversationType,
            answerCall,
            dispatcher
        )

        private val startCallUseCaseWithCBR = StartCallUseCase(
            lazy { callManager },
            syncManager,
            KaliumConfigs(forceConstantBitrateCalls = true),
            callRepository,
            getCallConversationType,
            answerCall,
            dispatcher
        )

        suspend fun withWaitingForSyncSucceeding() = withSyncReturning(Either.Right(Unit))
        suspend fun withAnIncomingCall() = apply {
            coEvery {
                callRepository.incomingCallsFlow()
            }.returns(flowOf(listOf(TestCall.groupIncomingCall(TestConversation.ID))))
        }

        suspend fun withCallConversationTypeUseCaseReturning(result: ConversationTypeCalling) = apply {
            coEvery {
                getCallConversationType.invoke(any())
            }.returns(result)
        }

        suspend fun withNoIncomingCall() = apply {
            coEvery {
                callRepository.incomingCallsFlow()
            }.returns(flowOf(listOf()))
        }

        suspend fun withWaitingForSyncFailing() =
            withSyncReturning(Either.Left(NetworkFailure.NoNetworkConnection(null)))

        private suspend fun withSyncReturning(result: Either<CoreFailure, Unit>) = apply {
            coEvery {
                syncManager.waitUntilLiveOrFailure()
            }.returns(result)
        }

        fun arrange() = this to startCallUseCase
        fun arrangeWithCBR() = this to startCallUseCaseWithCBR

    }
}
