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
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.CallingParticipantsOrder
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.call.Call
import com.wire.kalium.logic.data.call.CallStatus
import io.mockative.Mock
import io.mockative.coEvery
import io.mockative.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GetAllCallsWithSortedParticipantsUseCaseTest {

    @Mock
    private val callRepository = mock(CallRepository::class)

    @Mock
    private val callingParticipantsOrder = mock(CallingParticipantsOrder::class)

    private lateinit var getAllCallsWithSortedParticipantsUseCase: GetAllCallsWithSortedParticipantsUseCase

    @BeforeTest
    fun setUp() {
        getAllCallsWithSortedParticipantsUseCase = GetAllCallsWithSortedParticipantsUseCaseImpl(
            callRepository,
            callingParticipantsOrder
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenCallsFlowEmitsANewValue_whenUseCaseIsCollected_thenAssertThatTheUseCaseIsEmittingTheRightCalls() = runTest {
        val calls1 = listOf(call1, call2)
        val calls2 = listOf(call2)

        coEvery {
            callingParticipantsOrder.reorderItems(calls1.first().participants) 
        }.returns(calls1.first().participants)

        coEvery {
            callingParticipantsOrder.reorderItems(calls2.first().participants) 
        }.returns(calls2.first().participants)

        val callsFlow = flowOf(calls1, calls2)
        coEvery {
            callRepository.callsFlow()
        }.returns(callsFlow)

        val result = getAllCallsWithSortedParticipantsUseCase()

        result.test {
            assertEquals(calls1, awaitItem())
            assertEquals(calls2, awaitItem())
            awaitComplete()
        }
    }

    companion object {
        private val call1 = Call(
            ConversationId("first", "domain"),
            CallStatus.STARTED,
            true,
            false,
            false,
            "caller-id",
            "ONE_ON_ONE Name",
            Conversation.Type.ONE_ON_ONE,
            "otherUsername",
            "team1"
        )
        private val call2 = Call(
            ConversationId("second", "domain"),
            CallStatus.INCOMING,
            true,
            false,
            false,
            "caller-id",
            "ONE_ON_ONE Name",
            Conversation.Type.ONE_ON_ONE,
            "otherUsername2",
            "team2"
        )
    }

}
