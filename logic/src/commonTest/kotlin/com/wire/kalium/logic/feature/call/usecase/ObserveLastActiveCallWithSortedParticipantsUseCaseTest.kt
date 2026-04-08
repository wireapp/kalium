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
import com.wire.kalium.logic.data.call.CallingParticipantsOrderType
import com.wire.kalium.logic.data.call.Participant
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@Suppress("UnusedFlow")
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
        verify(VerifyMode.exactly(1)) {
            arrangement.callRepository.observeLastActiveCallByConversationId(establishedCall.conversationId)
        }
        verifySuspend(VerifyMode.exactly(0)) {
            arrangement.callingParticipantsOrder.reorderItems(establishedCall.participants)
        }
    }

    @Test
    fun givenActiveLastCall_whenUseCaseIsRunning_withVideosFirstOrder_thenAssertThatTheUseCaseIsEmittingTheRightCalls() = runTest {
        // given
        val call = establishedCall
        val (arrangement, useCase) = Arrangement()
            .withReorderItemsReturning(call.participants)
            .withObserveLastActiveCallByConversationIdReturning(flowOf(call))
            .arrange()
        // when
        useCase(call.conversationId, CallingParticipantsOrderType.VIDEOS_FIRST).test {
        // then
            assertEquals(call, awaitItem())
            awaitComplete()
        }
        verify(VerifyMode.exactly(1)) {
            arrangement.callRepository.observeLastActiveCallByConversationId(call.conversationId)
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.callingParticipantsOrder.reorderItems(call.participants, CallingParticipantsOrderType.VIDEOS_FIRST)
        }
    }

    @Test
    fun givenActiveLastCall_whenUseCaseIsRunning_withAlphabeticalOrder_thenAssertThatTheUseCaseIsEmittingTheRightCalls() = runTest {
        // given
        val call = establishedCall
        val (arrangement, useCase) = Arrangement()
            .withReorderItemsReturning(call.participants)
            .withObserveLastActiveCallByConversationIdReturning(flowOf(call))
            .arrange()
        // when
        useCase(call.conversationId, CallingParticipantsOrderType.ALPHABETICALLY).test {
            // then
            assertEquals(call, awaitItem())
            awaitComplete()
        }
        verify(VerifyMode.exactly(1)) {
            arrangement.callRepository.observeLastActiveCallByConversationId(call.conversationId)
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.callingParticipantsOrder.reorderItems(call.participants, CallingParticipantsOrderType.ALPHABETICALLY)
        }
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
        verify(VerifyMode.exactly(1)) {
            arrangement.callRepository.observeLastActiveCallByConversationId(establishedCall.conversationId)
        }
        verifySuspend(VerifyMode.exactly(2)) {
            arrangement.callingParticipantsOrder.reorderItems(establishedCall.participants)
        }
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
        verify(VerifyMode.exactly(1)) {
            arrangement.callRepository.observeLastActiveCallByConversationId(establishedCall.conversationId)
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.callingParticipantsOrder.reorderItems(establishedCall.participants)
        }
    }

    private inner class Arrangement {
        val callRepository = mock<CallRepository>()
        val callingParticipantsOrder = mock<CallingParticipantsOrder>()

        fun arrange() = this to ObserveLastActiveCallWithSortedParticipantsUseCaseImpl(
            callRepository,
            callingParticipantsOrder
        )
        suspend fun withReorderItemsReturning(result: List<Participant>) = apply {
            everySuspend {
                callingParticipantsOrder.reorderItems(any(), any())
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
