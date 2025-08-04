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
import com.wire.kalium.logic.data.call.Participant
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.every
import io.mockative.mock
import io.mockative.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ObserveLastActiveCallWithSortedParticipantsUseCaseTest {

    @Test
    fun givenNoActiveLastCall_whenUseCaseIsRunning_thenAssertThatTheUseCaseIsEmittingTheRightCalls() = runTest {
        // given
        val call = null
        val (arrangement, useCase) = Arrangement()
            .withObserveLastActiveCallByConversationIdReturning(flowOf(call))
            .arrange()
        // when
        useCase(establishedCall.conversationId).test {
        // then
            assertEquals(call, awaitItem())
            awaitComplete()
        }
        verify {
            arrangement.callRepository.observeLastActiveCallByConversationId(establishedCall.conversationId)
        }.wasInvoked(exactly = 1)
        coVerify {
            arrangement.callingParticipantsOrder.reorderItems(establishedCall.participants)
        }.wasInvoked(exactly = 0)
    }

    @Test
    fun givenActiveLastCall_whenUseCaseIsRunning_thenAssertThatTheUseCaseIsEmittingTheRightCalls() = runTest {
        // given
        val call = establishedCall
        val (arrangement, useCase) = Arrangement()
            .withReorderItemsReturning(call.participants)
            .withObserveLastActiveCallByConversationIdReturning(flowOf(call))
            .arrange()
        // when
        useCase(call.conversationId).test {
        // then
            assertEquals(call, awaitItem())
            awaitComplete()
        }
        verify {
            arrangement.callRepository.observeLastActiveCallByConversationId(call.conversationId)
        }.wasInvoked(exactly = 1)
        coVerify {
            arrangement.callingParticipantsOrder.reorderItems(call.participants)
        }.wasInvoked(exactly = 1)
    }

    @Test
    fun givenActiveLastCallIsUpdated_whenUseCaseIsRunning_thenAssertThatTheUseCaseIsEmittingTheRightCalls() = runTest {
        // given
        val call = establishedCall.copy(status = CallStatus.ANSWERED)
        val updatedCall = establishedCall.copy(status = CallStatus.ESTABLISHED)
        val (arrangement, useCase) = Arrangement()
            .withReorderItemsReturning(call.participants)
            .withObserveLastActiveCallByConversationIdReturning(flowOf(call, updatedCall))
            .arrange()
        // when
        useCase(call.conversationId).test {
        // then
            assertEquals(call, awaitItem())
            assertEquals(updatedCall, awaitItem())
            awaitComplete()
        }
        verify {
            arrangement.callRepository.observeLastActiveCallByConversationId(establishedCall.conversationId)
        }.wasInvoked(exactly = 1)
        coVerify {
            arrangement.callingParticipantsOrder.reorderItems(establishedCall.participants)
        }.wasInvoked(exactly = 2)
    }

    @Test
    fun givenActiveLastCallStopsBeingActive_whenUseCaseIsRunning_thenAssertThatTheUseCaseIsEmittingTheRightCalls() = runTest {
        // given
        val call = establishedCall.copy(status = CallStatus.ANSWERED)
        val updatedCall = null
        val (arrangement, useCase) = Arrangement()
            .withReorderItemsReturning(call.participants)
            .withObserveLastActiveCallByConversationIdReturning(flowOf(call, updatedCall))
            .arrange()
        // when
        useCase(call.conversationId).test {
        // then
            assertEquals(call, awaitItem())
            assertEquals(updatedCall, awaitItem())
            awaitComplete()
        }
        verify {
            arrangement.callRepository.observeLastActiveCallByConversationId(establishedCall.conversationId)
        }.wasInvoked(exactly = 1)
        coVerify {
            arrangement.callingParticipantsOrder.reorderItems(establishedCall.participants)
        }.wasInvoked(exactly = 1)
    }

    private inner class Arrangement {
        val callRepository = mock(CallRepository::class)
        val callingParticipantsOrder = mock(CallingParticipantsOrder::class)

        fun arrange() = this to ObserveLastActiveCallWithSortedParticipantsUseCaseImpl(
            callRepository,
            callingParticipantsOrder
        )
        suspend fun withReorderItemsReturning(result: List<Participant>) = apply {
            coEvery {
                callingParticipantsOrder.reorderItems(any())
            }.returns(result)
        }
        fun withObserveLastActiveCallByConversationIdReturning(result: Flow<Call?>) = apply {
            every {
                callRepository.observeLastActiveCallByConversationId(any())
            }.returns(result)
        }
    }

    companion object Companion {
        private val establishedCall = Call(
            ConversationId("second", "domain"),
            CallStatus.ESTABLISHED,
            isMuted = true,
            isCameraOn = false,
            isCbrEnabled = false,
            callerId = UserId("called-id", "domain"),
            conversationName = "ONE_ON_ONE Name",
            conversationType = Conversation.Type.OneOnOne,
            callerName = "otherUsername2",
            callerTeamName = "team2"
        )
    }
}
