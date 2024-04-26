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
import io.mockative.Mock
import io.mockative.coEvery
import io.mockative.mock
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ObserveOutgoingCallUseCaseTest {

    @Test
    fun givenCallRepositoryEmitsValues_whenObserveOutgoingCall_thenEmitOutgoingCalls() = runTest {
        val (_, observeOutgoingCall) = Arrangement()
            .withCallRepositoryEmitsValues()
            .arrange()

        val result = observeOutgoingCall()

        result.collect {
            assertEquals(listOf(outgoingCall), it)
        }
    }

    private class Arrangement {

        @Mock
        val callRepository = mock(CallRepository::class)

        val observeOutgoingCall = ObserveOutgoingCallUseCaseImpl(callRepository)

        fun arrange() = this to observeOutgoingCall

        suspend fun withCallRepositoryEmitsValues() = apply {
            coEvery {
                callRepository.outgoingCallsFlow()
            }.returns(flowOf(listOf(outgoingCall)))
        }
    }

    companion object {
        val outgoingCall = Call(
            conversationId = ConversationId(
                value = "conversationId",
                domain = "conversationDomain"
            ),
            status = CallStatus.STARTED,
            callerId = "callerId@domain",
            participants = listOf(),
            isMuted = true,
            isCameraOn = false,
            isCbrEnabled = false,
            maxParticipants = 0,
            conversationName = "ONE_ON_ONE Name",
            conversationType = Conversation.Type.ONE_ON_ONE,
            callerName = "otherUsername",
            callerTeamName = "team_1"
        )
    }
}
