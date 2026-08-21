/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
import com.wire.kalium.logic.data.call.EndCallOnMLSResetUseCase
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.framework.TestCall
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class EndCallOnMLSResetUseCaseTest {

    @Test
    fun givenActiveOneOnOneCall_whenInvoked_thenCallIsClosedAndEnded() = runTest {
        val call = TestCall.oneOnOneEstablishedCall()
        val (arrangement, useCase) = Arrangement().arrange(listOf(call))

        useCase(call.conversationId)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.callRepository.updateCallStatusById(eq(call.conversationId), eq(CallStatus.CLOSED))
        }
        verify(VerifyMode.exactly(1)) {
            arrangement.callRepository.updateIsCameraOnById(eq(call.conversationId), eq(false))
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.callManager.endCall(eq(call.conversationId))
        }
    }

    @Test
    fun givenAnsweredGroupCall_whenInvoked_thenCallIsClosedAndEnded() = runTest {
        val call = TestCall.oneOnOneEstablishedCall().copy(
            status = CallStatus.ANSWERED,
            conversationType = Conversation.Type.Group.Regular,
        )
        val (arrangement, useCase) = Arrangement().arrange(listOf(call))

        useCase(call.conversationId)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.callRepository.updateCallStatusById(eq(call.conversationId), eq(CallStatus.CLOSED))
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.callManager.endCall(eq(call.conversationId))
        }
    }

    @Test
    fun givenNoActiveCallForConversation_whenInvoked_thenNothingIsChanged() = runTest {
        val call = TestCall.oneOnOneEstablishedCall()
        val (arrangement, useCase) = Arrangement().arrange(emptyList())

        useCase(call.conversationId)

        verifySuspend(VerifyMode.not) {
            arrangement.callRepository.updateCallStatusById(any(), any())
        }
        verify(VerifyMode.not) {
            arrangement.callRepository.updateIsCameraOnById(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.callManager.endCall(any())
        }
    }

    @Test
    fun givenConcurrentInvocationsForTheSameCall_whenInvoked_thenCallIsEndedOnce() = runTest {
        val call = TestCall.oneOnOneEstablishedCall()
        val (arrangement, useCase) = Arrangement().arrange(listOf(call))

        awaitAll(
            async { useCase(call.conversationId) },
            async { useCase(call.conversationId) },
        )

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.callManager.endCall(eq(call.conversationId))
        }
    }

    private class Arrangement {
        val callRepository = mock<CallRepository>(mode = MockMode.autoUnit)
        val callManager = mock<CallManager>(mode = MockMode.autoUnit)
        private val activeCalls = MutableStateFlow<List<Call>>(emptyList())

        suspend fun arrange(
            calls: List<Call>,
            endCallFailure: Exception? = null,
            updateStatusFailure: Exception? = null,
        ): Pair<Arrangement, EndCallOnMLSResetUseCase> {
            activeCalls.value = calls
            every { callRepository.activeCallsFlow() } returns activeCalls
            every { callRepository.updateIsCameraOnById(any(), any()) } returns Unit

            if (updateStatusFailure == null) {
                everySuspend {
                    callRepository.updateCallStatusById(any(), any())
                } calls {
                    activeCalls.value = emptyList()
                }
            } else {
                everySuspend {
                    callRepository.updateCallStatusById(any(), any())
                } throws updateStatusFailure
            }

            if (endCallFailure == null) {
                everySuspend { callManager.endCall(any()) } returns Unit
            } else {
                everySuspend { callManager.endCall(any()) } throws endCallFailure
            }

            return this to EndCallOnMLSResetUseCaseImpl(
                callManager = lazy { callManager },
                callRepository = callRepository,
            )
        }
    }
}
