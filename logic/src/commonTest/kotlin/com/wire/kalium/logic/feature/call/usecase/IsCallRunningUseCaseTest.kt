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

package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.Call
import com.wire.kalium.logic.feature.call.CallStatus
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class IsCallRunningUseCaseTest {

    @Mock
    private val callRepository = mock(classOf<CallRepository>())

    private lateinit var isCallRunningUseCase: IsCallRunningUseCase

    @BeforeTest
    fun setUp() {
        isCallRunningUseCase = IsCallRunningUseCase(
            callRepository = callRepository
        )
    }

    @Test
    fun givenAFlowWithEmptyValues_whenInvokingUseCase_thenReturnsFalse() = runTest {
        given(callRepository)
            .suspendFunction(callRepository::callsFlow).whenInvoked()
            .thenReturn(flowOf(listOf()))

        val result = isCallRunningUseCase()

        assertEquals(false, result)
    }

    @Test
    fun givenAFlowThatDoesNotContainIncomingOrOutgoingOrOngoingCall_whenInvokingUseCase_thenReturnsFalse() = runTest {
        given(callRepository)
            .suspendFunction(callRepository::callsFlow).whenInvoked()
            .thenReturn(flowOf(listOf(call2)))

        val result = isCallRunningUseCase()

        assertEquals(false, result)
    }

    @Test
    fun givenAFlowContainingAnIncomingCall_whenInvokingUseCase_thenReturnsTrue() = runTest {
        given(callRepository)
            .suspendFunction(callRepository::callsFlow).whenInvoked()
            .thenReturn(flowOf(listOf(call1, call2)))

        val result = isCallRunningUseCase()

        assertEquals(true, result)
    }

    companion object {
        private val call1 = Call(
            ConversationId("first", "domain"),
            CallStatus.STARTED,
            true,
            false,
            false,
            "caller-id1",
            "ONE_ON_ONE Name",
            Conversation.Type.ONE_ON_ONE,
            "otherUsername",
            "team1"
        )
        private val call2 = Call(
            ConversationId("second", "domain"),
            CallStatus.CLOSED,
            true,
            false,
            false,
            "caller-id2",
            "ONE_ON_ONE Name",
            Conversation.Type.ONE_ON_ONE,
            "otherUsername",
            "team1"
        )
    }

}
