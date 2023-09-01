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
package com.wire.kalium.logic.feature.scenario

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.Call
import com.wire.kalium.logic.feature.call.CallStatus
import com.wire.kalium.logic.feature.call.scenario.OnMuteStateForSelfUserChanged
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Test

class OnMuteStateForSelfUserChangedTest {

    @Test
    fun givenNoOngoingCall_whenMuteStateCallbackHappens_thenNothingToDo() = runTest(testScope) {
        val (arrangement, onMuteStateForSelfUserChanged) = Arrangement()
            .givenNoOngoingCall()
            .arrange()

        onMuteStateForSelfUserChanged.onMuteStateChanged(1, null)

        verify(arrangement.callRepository)
            .function(arrangement.callRepository::updateIsMutedById)
            .with(any(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenAnOngoingCall_whenMuteStateCallbackHappens_thenUpdateMuteState() = runTest(testScope) {
        val (arrangement, onMuteStateForSelfUserChanged) = Arrangement()
            .givenAnOngoingCall()
            .arrange()

        onMuteStateForSelfUserChanged.onMuteStateChanged(1, null)

        yield()

        verify(arrangement.callRepository)
            .function(arrangement.callRepository::updateIsMutedById)
            .with(eq(conversationId), eq(true))
            .wasInvoked(once)
    }

    companion object {
        val conversationId = ConversationId("conversationId", "domainId")
        private val testScope = TestKaliumDispatcher.main
    }

    internal class Arrangement {

        @Mock
        val callRepository = mock(classOf<CallRepository>())

        fun arrange() = this to OnMuteStateForSelfUserChanged(
            CoroutineScope(testScope),
            callRepository,
        )

        fun givenNoOngoingCall() = apply {
            given(callRepository)
                .suspendFunction(callRepository::establishedCallsFlow)
                .whenInvoked()
                .thenReturn(flowOf(listOf()))
        }

        fun givenAnOngoingCall() = apply {
            given(callRepository)
                .suspendFunction(callRepository::establishedCallsFlow)
                .whenInvoked()
                .thenReturn(flowOf(listOf(call)))
        }

        companion object {
            private val call = Call(
                conversationId = conversationId,
                status = CallStatus.ESTABLISHED,
                callerId = "called-id",
                isMuted = false,
                isCameraOn = false,
                isCbrEnabled = false,
                conversationName = null,
                conversationType = Conversation.Type.GROUP,
                callerName = null,
                callerTeamName = null,
                establishedTime = null
            )
        }
    }
}
