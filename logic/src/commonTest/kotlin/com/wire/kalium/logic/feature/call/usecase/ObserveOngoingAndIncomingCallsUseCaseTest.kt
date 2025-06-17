/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
import com.wire.kalium.logic.feature.call.usecase.AnswerCallUseCaseTest.Companion.conversationId
import com.wire.kalium.logic.framework.TestCall
import io.mockative.coEvery
import io.mockative.mock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ObserveOngoingAndIncomingCallsUseCaseTest {

    val callRepository = mock(CallRepository::class)

    private lateinit var observeOngoingAndIncomingCalls: ObserveOngoingAndIncomingCallsUseCase

    @BeforeTest
    fun setUp() {
        observeOngoingAndIncomingCalls = ObserveOngoingAndIncomingCallsUseCaseImpl(
            callRepository = callRepository,
        )
    }

    @Test
    fun givenCallsWithDifferentStatuses_whenInvokeIsCalled_thenItFiltersIncomingAndOngoingCalls() = runTest {

        coEvery {
            callRepository.callsFlow()
        }.returns(
            flowOf(
                listOf(
                    call.copy(status = CallStatus.STARTED),
                    call.copy(status = CallStatus.ESTABLISHED),
                    call.copy(status = CallStatus.INCOMING),
                    call.copy(status = CallStatus.STILL_ONGOING),
                )
            )
        )

        val result = observeOngoingAndIncomingCalls.invoke()

        result.first().let {
            assertEquals(2, it.size)
            assertEquals(CallStatus.INCOMING, it[0].status)
            assertEquals(CallStatus.STILL_ONGOING, it[1].status)
        }
    }

    companion object {
        val call = Call(
            conversationId = conversationId,
            status = CallStatus.INCOMING,
            isMuted = true,
            isCameraOn = false,
            isCbrEnabled = false,
            callerId = TestCall.CALLER_ID,
            conversationName = "caller-name",
            conversationType = Conversation.Type.Group.Regular,
            callerName = "Name",
            callerTeamName = "group",
            establishedTime = null
        )
    }
}
