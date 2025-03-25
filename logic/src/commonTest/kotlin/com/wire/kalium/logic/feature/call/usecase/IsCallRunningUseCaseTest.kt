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

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.call.Call
import com.wire.kalium.logic.data.call.CallStatus
import com.wire.kalium.logic.data.user.UserId
import io.mockative.Mock
import io.mockative.coEvery
import io.mockative.mock
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class IsCallRunningUseCaseTest {

    @Mock
    private val callRepository = mock(CallRepository::class)

    private lateinit var isCallRunningUseCase: IsCallRunningUseCase

    @BeforeTest
    fun setUp() {
        isCallRunningUseCase = IsCallRunningUseCase(
            callRepository = callRepository
        )
    }

    @Test
    fun givenAFlowWithEmptyValues_whenInvokingUseCase_thenReturnsFalse() = runTest {
        coEvery {
            callRepository.callsFlow()
        }.returns(flowOf(listOf()))

        val result = isCallRunningUseCase()

        assertEquals(false, result)
    }

    @Test
    fun givenAFlowThatDoesNotContainIncomingOrOutgoingOrOngoingCall_whenInvokingUseCase_thenReturnsFalse() = runTest {
        coEvery {
            callRepository.callsFlow()
        }.returns(flowOf(listOf(call2)))

        val result = isCallRunningUseCase()

        assertEquals(false, result)
    }

    @Test
    fun givenAFlowContainingAnIncomingCall_whenInvokingUseCase_thenReturnsTrue() = runTest {
        coEvery {
            callRepository.callsFlow()
        }.returns(flowOf(listOf(call1, call2)))

        val result = isCallRunningUseCase()

        assertEquals(true, result)
    }

    companion object {
        private val call1 = Call(
            ConversationId("first", "domain"),
            CallStatus.STARTED,
            isMuted = true,
            isCameraOn = false,
            isCbrEnabled = false,
            callerId = UserId("caller-id1", "domain"),
            conversationName = "ONE_ON_ONE Name",
            conversationType = Conversation.Type.OneOnOne,
            callerName = "otherUsername",
            callerTeamName = "team1"
        )
        private val call2 = Call(
            ConversationId("second", "domain"),
            CallStatus.CLOSED,
            isMuted = true,
            isCameraOn = false,
            isCbrEnabled = false,
            callerId = UserId("caller-id2", "domain"),
            conversationName = "ONE_ON_ONE Name",
            conversationType = Conversation.Type.OneOnOne,
            callerName = "otherUsername",
            callerTeamName = "team1"
        )
    }

}
