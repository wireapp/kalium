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
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.framework.TestCall
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.every
import io.mockative.mock
import io.mockative.verify
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class EndCallOnMLSResetUseCaseTest {

    @Test
    fun givenActiveOneOnOneCall_whenInvoked_thenCallIsClosedAndEnded() = runTest {
        val call = TestCall.oneOnOneEstablishedCall()
        val (arrangement, useCase) = Arrangement().arrange(listOf(call))

        useCase(call.conversationId)

        assertEquals(listOf("close", "cameraOff", "endCall"), arrangement.steps)
        coVerify {
            arrangement.callRepository.updateCallStatusById(eq(call.conversationId), eq(CallStatus.CLOSED))
        }.wasInvoked(exactly = 1)
        verify {
            arrangement.callRepository.updateIsCameraOnById(eq(call.conversationId), eq(false))
        }.wasInvoked(exactly = 1)
        coVerify {
            arrangement.callManager.endCall(eq(call.conversationId))
        }.wasInvoked(exactly = 1)
    }

    @Test
    fun givenAnsweredGroupCall_whenInvoked_thenCallIsClosedAndEnded() = runTest {
        val call = TestCall.oneOnOneEstablishedCall().copy(
            status = CallStatus.ANSWERED,
            conversationType = Conversation.Type.Group.Regular,
        )
        val (arrangement, useCase) = Arrangement().arrange(listOf(call))

        useCase(call.conversationId)

        coVerify {
            arrangement.callRepository.updateCallStatusById(eq(call.conversationId), eq(CallStatus.CLOSED))
        }.wasInvoked(exactly = 1)
        coVerify {
            arrangement.callManager.endCall(eq(call.conversationId))
        }.wasInvoked(exactly = 1)
    }

    @Test
    fun givenNoActiveCallForConversation_whenInvoked_thenNothingIsChanged() = runTest {
        val call = TestCall.oneOnOneEstablishedCall()
        val (arrangement, useCase) = Arrangement().arrange(emptyList())

        useCase(call.conversationId)

        coVerify {
            arrangement.callRepository.updateCallStatusById(any(), any())
        }.wasNotInvoked()
        verify {
            arrangement.callRepository.updateIsCameraOnById(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.callManager.endCall(any())
        }.wasNotInvoked()
    }

    @Test
    fun givenConcurrentInvocationsForTheSameCall_whenInvoked_thenCallIsEndedOnce() = runTest {
        val call = TestCall.oneOnOneEstablishedCall()
        val (arrangement, useCase) = Arrangement().arrange(listOf(call))

        awaitAll(
            async { useCase(call.conversationId) },
            async { useCase(call.conversationId) },
        )

        coVerify {
            arrangement.callManager.endCall(eq(call.conversationId))
        }.wasInvoked(exactly = 1)
    }

    @Test
    fun givenClosedCall_whenInvoked_thenCallIsNotEndedAgain() = runTest {
        val call = TestCall.oneOnOneEstablishedCall().copy(status = CallStatus.CLOSED)
        val (arrangement, useCase) = Arrangement().arrange(listOf(call))

        useCase(call.conversationId)

        assertEquals(emptyList(), arrangement.steps)
    }

    @Test
    fun givenActiveCallInAnotherConversation_whenInvoked_thenCallIsUnchanged() = runTest {
        val call = TestCall.oneOnOneEstablishedCall()
        val (arrangement, useCase) = Arrangement().arrange(listOf(call))

        useCase(call.conversationId.copy(value = "another-conversation"))

        assertEquals(emptyList(), arrangement.steps)
    }

    private class Arrangement {
        val steps = mutableListOf<String>()
        val callRepository = mock(CallRepository::class)
        val callManager = mock(CallManager::class)

        suspend fun arrange(calls: List<Call>): Pair<Arrangement, EndCallOnMLSResetUseCase> {
            val metadata = calls.associate {
                it.conversationId to TestCall.oneOnOneCallMetadata().copy(callStatus = it.status)
            }.toMutableMap()
            every { callRepository.getCallMetadata(any()) }.invokes { args ->
                metadata[args.first() as ConversationId]
            }
            every { callRepository.updateIsCameraOnById(any(), any()) }.invokes {
                steps.add("cameraOff")
                Unit
            }
            coEvery { callRepository.updateCallStatusById(any(), any()) }.invokes { args ->
                val id = args.first() as ConversationId
                metadata[id]?.let { metadata[id] = it.copy(callStatus = CallStatus.CLOSED) }
                steps.add("close")
                Unit
            }
            coEvery { callManager.endCall(any()) }.invokes {
                steps.add("endCall")
                Unit
            }

            return this to EndCallOnMLSResetUseCaseImpl(
                callManager = lazy { callManager },
                callRepository = callRepository,
            )
        }
    }
}
