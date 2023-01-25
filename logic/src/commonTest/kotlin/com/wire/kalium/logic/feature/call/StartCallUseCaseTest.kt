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

package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.call.CallType
import com.wire.kalium.logic.data.call.ConversationType
import com.wire.kalium.logic.feature.call.usecase.StartCallUseCase
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
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class StartCallUseCaseTest {

    @Test
    fun givenCallingParamsAndSyncSucceeds_whenRunningUseCase_thenInvokeStartCallOnce() = runTest {
        val conversationId = TestConversation.ID

        val (arrangement, startCall) = Arrangement()
            .withWaitingForSyncSucceeding()
            .arrange()

        startCall.invoke(conversationId, CallType.AUDIO, ConversationType.OneOnOne)

        verify(arrangement.callManager)
            .suspendFunction(arrangement.callManager::startCall)
            .with(eq(conversationId), eq(CallType.AUDIO), eq(ConversationType.OneOnOne), eq(false))
            .wasInvoked(once)
    }

    @Test
    fun givenCallingParamsAndSyncSucceeds_whenRunningUseCase_thenReturnSuccess() = runTest {
        val conversationId = TestConversation.ID

        val (_, startCall) = Arrangement()
            .withWaitingForSyncSucceeding()
            .arrange()

        val result = startCall.invoke(conversationId, CallType.AUDIO, ConversationType.OneOnOne)

        assertIs<StartCallUseCase.Result.Success>(result)
    }

    @Test
    fun givenCallingParamsAndSyncFails_whenRunningUseCase_thenStartCallIsNotInvoked() = runTest {
        val conversationId = TestConversation.ID

        val (arrangement, startCall) = Arrangement()
            .withWaitingForSyncFailing()
            .arrange()

        startCall.invoke(conversationId, CallType.AUDIO, ConversationType.OneOnOne)

        verify(arrangement.callManager)
            .suspendFunction(arrangement.callManager::startCall)
            .with(any(), any(), any(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenCallingParamsAndSyncFails_whenRunningUseCase_thenShouldReturnSyncFailure() = runTest {
        val conversationId = TestConversation.ID

        val (_, startCall) = Arrangement()
            .withWaitingForSyncFailing()
            .arrange()

        val result = startCall.invoke(conversationId, CallType.AUDIO, ConversationType.OneOnOne)

        assertIs<StartCallUseCase.Result.SyncFailure>(result)
    }

    private class Arrangement {

        @Mock
        val callManager = configure(mock(classOf<CallManager>())) { stubsUnitByDefault = true }

        @Mock
        val syncManager = mock(classOf<SyncManager>())

        private val startCallUseCase = StartCallUseCase(
            lazy { callManager }, syncManager
        )

        fun withWaitingForSyncSucceeding() = withSyncReturning(Either.Right(Unit))

        fun withWaitingForSyncFailing() = withSyncReturning(Either.Left(NetworkFailure.NoNetworkConnection(null)))

        private fun withSyncReturning(result: Either<CoreFailure, Unit>) = apply {
            given(syncManager)
                .suspendFunction(syncManager::waitUntilLiveOrFailure)
                .whenInvoked()
                .then { result }
        }

        fun arrange() = this to startCallUseCase

    }
}
