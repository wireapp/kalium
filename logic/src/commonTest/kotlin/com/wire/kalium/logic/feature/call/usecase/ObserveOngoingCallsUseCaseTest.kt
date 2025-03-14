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
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.call.Call
import com.wire.kalium.logic.data.call.CallStatus
import com.wire.kalium.logic.framework.TestCall
import io.mockative.Mock
import io.mockative.coEvery
import io.mockative.mock
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ObserveOngoingCallsUseCaseTest {

    @Mock
    val callRepository = mock(CallRepository::class)

    private lateinit var observeOngoingCalls: ObserveOngoingCallsUseCase

    @BeforeTest
    fun setUp() {
        observeOngoingCalls = ObserveOngoingCallsUseCaseImpl(
            callRepository = callRepository,
        )
    }

    @Test
    fun givenAnEmptyCallList_whenInvokingObserveOngoingCallsUseCase_thenEmitsAnEmptyListOfCalls() = runTest {
        coEvery {
            callRepository.ongoingCallsFlow()
        }.returns(flowOf(listOf()))

        val result = observeOngoingCalls()

        result.test {
            assertEquals(listOf(), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun givenAnOngoingCallList_whenInvokingObserveOngoingCallsUseCase_thenEmitsAnOngoingListOfCalls() = runTest {
        coEvery {
            callRepository.ongoingCallsFlow()
        }.returns(flowOf(listOf(DUMMY_CALL)))

        val result = observeOngoingCalls()

        result.test {
            assertEquals(listOf(DUMMY_CALL), awaitItem())
            awaitComplete()
        }
    }

    private companion object {
        val DUMMY_CALL = Call(
            conversationId = ConversationId(
                value = "convId",
                domain = "domainId"
            ),
            status = CallStatus.STILL_ONGOING,
            isMuted = false,
            isCameraOn = false,
            isCbrEnabled = false,
            callerId = TestCall.CALLER_ID,
            conversationName = null,
            conversationType = Conversation.Type.Group.Regular,
            callerName = null,
            callerTeamName = null
        )
    }
}
