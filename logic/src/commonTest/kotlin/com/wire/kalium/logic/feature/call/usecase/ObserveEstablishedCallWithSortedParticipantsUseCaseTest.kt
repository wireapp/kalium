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

import app.cash.turbine.test
import com.wire.kalium.logic.data.call.Call
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.CallStatus
import com.wire.kalium.logic.data.call.CallingParticipantsOrder
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import io.mockative.Mock
import io.mockative.coEvery
import io.mockative.mock
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ObserveEstablishedCallWithSortedParticipantsUseCaseTest {

    @Mock
    private val callRepository = mock(CallRepository::class)

    @Mock
    private val callingParticipantsOrder = mock(CallingParticipantsOrder::class)

    private lateinit var observeEstablishedCallWithSortedParticipantsUseCase: ObserveEstablishedCallWithSortedParticipantsUseCase

    @BeforeTest
    fun setUp() {
        observeEstablishedCallWithSortedParticipantsUseCase = ObserveEstablishedCallWithSortedParticipantsUseCaseImpl(
            callRepository,
            callingParticipantsOrder
        )
    }

    @Test
    fun givenCallFlowEmitsANewValue_whenUseCaseIsRunning_thenAssertThatTheUseCaseIsEmittingTheRightCalls() = runTest {
        val calls = listOf(establishedCall)

        coEvery {
            callingParticipantsOrder.reorderItems(calls.first().participants)
        }.returns(calls.first().participants)

        coEvery {
            callRepository.establishedCallsFlow()
        }.returns(flowOf(calls))

        val result = observeEstablishedCallWithSortedParticipantsUseCase()

        result.test {
            assertEquals(establishedCall, awaitItem())
            awaitComplete()
        }
    }

    companion object {
        private val establishedCall = Call(
            ConversationId("second", "domain"),
            CallStatus.ESTABLISHED,
            isMuted = true,
            isCameraOn = false,
            isCbrEnabled = false,
            callerId = UserId("called-id", "domain"),
            conversationName = "ONE_ON_ONE Name",
            conversationType = Conversation.Type.ONE_ON_ONE,
            callerName = "otherUsername2",
            callerTeamName = "team2"
        )
    }
}
