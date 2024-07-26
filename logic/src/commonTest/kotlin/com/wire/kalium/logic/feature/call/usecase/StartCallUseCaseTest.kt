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
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.configure
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class StartCallUseCaseTest {

    @Test
    fun givenAnIncomingCall_whenStartingANewCall_thenAnswerCallUseCaseShouldBeInvokedOnce() =
        runTest {
            val conversationId = TestConversation.ID

            val (arrangement, startCall) = Arrangement()
                .withWaitingForSyncSucceeding()
                .withAnIncomingCall()
                .withCallConversationTypeUseCaseReturning(ConversationTypeCalling.Conference)
                .arrange()

            startCall.invoke(conversationId)

            verify(arrangement.answerCall)
                .suspendFunction(arrangement.answerCall::invoke)
                .with(eq(conversationId))
                .wasInvoked(once)

            verify(arrangement.callManager)
                .suspendFunction(arrangement.callManager::startCall)
                .with(any(), any(), any())
                .wasNotInvoked()
        }

    @Test
    fun givenCallingParamsAndSyncSucceeds_whenRunningUseCase_thenInvokeStartCallOnce() = runTest {
        val conversationId = TestConversation.ID

        val (arrangement, startCall) = Arrangement()
            .withWaitingForSyncSucceeding()
            .withNoIncomingCall()
            .withCallConversationTypeUseCaseReturning(ConversationTypeCalling.Conference)
            .arrange()

        startCall.invoke(conversationId, CallType.AUDIO)

        verify(arrangement.callManager)
            .suspendFunction(arrangement.callManager::startCall)
            .with(eq(conversationId), eq(CallType.AUDIO), eq(ConversationTypeCalling.Conference), eq(false))
            .wasInvoked(once)

        verify(arrangement.answerCall)
            .suspendFunction(arrangement.answerCall::invoke)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenCallingParamsAndSyncSucceeds_whenRunningUseCase_thenReturnSuccess() = runTest {
        val conversationId = TestConversation.ID

        val (arrangement, startCall) = Arrangement()
            .withWaitingForSyncSucceeding()
            .withNoIncomingCall()
            .withCallConversationTypeUseCaseReturning(ConversationTypeCalling.Conference)
            .arrange()

        val result = startCall.invoke(conversationId, CallType.AUDIO)

        assertIs<StartCallUseCase.Result.Success>(result)
        verify(arrangement.answerCall)
            .suspendFunction(arrangement.answerCall::invoke)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenCallingParamsAndSyncFails_whenRunningUseCase_thenStartCallIsNotInvoked() = runTest {
        val conversationId = TestConversation.ID

        val (arrangement, startCall) = Arrangement()
            .withWaitingForSyncFailing()
            .withCallConversationTypeUseCaseReturning(ConversationTypeCalling.OneOnOne)
            .arrange()

        startCall.invoke(conversationId, CallType.AUDIO)

        verify(arrangement.callManager)
            .suspendFunction(arrangement.callManager::startCall)
            .with(any(), any(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenCallingParamsAndSyncFails_whenRunningUseCase_thenShouldReturnSyncFailure() = runTest {
        val conversationId = TestConversation.ID

        val (_, startCall) = Arrangement()
            .withWaitingForSyncFailing()
            .arrange()

        val result = startCall.invoke(conversationId, CallType.AUDIO)

        assertIs<StartCallUseCase.Result.SyncFailure>(result)
    }

    @Test
    fun givenCbrEnabled_WhenStartingACall_thenStartTheCallOnCBR() = runTest {
        val conversationId = TestConversation.ID

        val (arrangement, startCall) = Arrangement()
            .withWaitingForSyncSucceeding()
            .withNoIncomingCall()
            .withCallConversationTypeUseCaseReturning(ConversationTypeCalling.Conference)
            .arrangeWithCBR()

        startCall.invoke(conversationId, CallType.AUDIO)

        verify(arrangement.callManager)
            .suspendFunction(arrangement.callManager::startCall)
            .with(eq(conversationId), eq(CallType.AUDIO), eq(ConversationTypeCalling.Conference), eq(true))
            .wasInvoked(once)
        verify(arrangement.answerCall)
            .suspendFunction(arrangement.answerCall::invoke)
            .with(any())
            .wasNotInvoked()
    }

    private class Arrangement {

        @Mock
        val callManager = configure(mock(classOf<CallManager>())) { stubsUnitByDefault = true }

        @Mock
        val syncManager = mock(classOf<SyncManager>())

        @Mock
        val answerCall = mock(classOf<AnswerCallUseCase>())

        @Mock
        val getCallConversationType = mock(GetCallConversationTypeProvider::class)

        @Mock
        val callRepository = mock(classOf<CallRepository>())

        private val kaliumConfigs = KaliumConfigs()

        private val startCallUseCase = StartCallUseCase(
            lazy { callManager }, syncManager, kaliumConfigs, callRepository, getCallConversationType, answerCall
        )

        private val startCallUseCaseWithCBR = StartCallUseCase(
            lazy { callManager },
            syncManager,
            KaliumConfigs(forceConstantBitrateCalls = true),
            callRepository,
            getCallConversationType,
            answerCall,
        )

        fun withWaitingForSyncSucceeding() = withSyncReturning(Either.Right(Unit))
        fun withAnIncomingCall() = apply {
            given(callRepository)
                .suspendFunction(callRepository::incomingCallsFlow)
                .whenInvoked()
                .then {
                    flowOf(listOf(TestCall.groupIncomingCall(TestConversation.ID)))
                }
        }

        suspend fun withCallConversationTypeUseCaseReturning(result: ConversationTypeCalling) = apply {
            given(getCallConversationType)
                .suspendFunction(getCallConversationType::invoke)
                .whenInvokedWith(any())
                .then { result }
        }

        fun withNoIncomingCall() = apply {
            given(callRepository)
                .suspendFunction(callRepository::incomingCallsFlow)
                .whenInvoked()
                .then { flowOf(listOf()) }
        }

        fun withWaitingForSyncFailing() =
            withSyncReturning(Either.Left(NetworkFailure.NoNetworkConnection(null)))

        private fun withSyncReturning(result: Either<CoreFailure, Unit>) = apply {
            given(syncManager)
                .suspendFunction(syncManager::waitUntilLiveOrFailure)
                .whenInvoked()
                .then { result }
        }

        fun arrange() = this to startCallUseCase
        fun arrangeWithCBR() = this to startCallUseCaseWithCBR

    }
}
