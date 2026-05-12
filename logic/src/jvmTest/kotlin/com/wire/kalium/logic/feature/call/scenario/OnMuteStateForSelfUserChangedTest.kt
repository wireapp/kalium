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
package com.wire.kalium.logic.feature.call.scenario

import com.wire.kalium.logic.data.call.Call
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.CallStatus
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.scenario.OnMuteStateForSelfUserChanged
import com.wire.kalium.logic.framework.TestCall
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.verify.VerifyMode
import dev.mokkery.matcher.any
import dev.mokkery.everySuspend
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify
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

        verify(VerifyMode.not) {
            arrangement.callRepository.updateIsMutedById(any(), any())
        }
    }

    @Test
    fun givenAnOngoingCall_whenMuteStateCallbackHappens_thenUpdateMuteState() = runTest(testScope) {
        val (arrangement, onMuteStateForSelfUserChanged) = Arrangement()
            .givenAnOngoingCall()
            .arrange()

        onMuteStateForSelfUserChanged.onMuteStateChanged(1, null)

        yield()

        verify(VerifyMode.exactly(1)) {
            arrangement.callRepository.updateIsMutedById(eq(conversationId), eq(true))
        }
    }

    companion object {
        val conversationId = ConversationId("conversationId", "domainId")
        private val testScope = TestKaliumDispatcher.main
    }

    internal class Arrangement {
        val callRepository = mock<CallRepository>(mode = MockMode.autoUnit)

        fun arrange() = this to OnMuteStateForSelfUserChanged(
            CoroutineScope(testScope),
            callRepository,
        )

        suspend fun givenNoOngoingCall() = apply {
            everySuspend {
                callRepository.establishedCallsFlow()
            } returns (flowOf(listOf()))
        }

        suspend fun givenAnOngoingCall() = apply {
            everySuspend {
                callRepository.establishedCallsFlow()
            } returns (flowOf(listOf(call)))
        }

        companion object {
            private val call = Call(
                conversationId = conversationId,
                status = CallStatus.ESTABLISHED,
                callerId = TestCall.CALLER_ID,
                isMuted = false,
                isCameraOn = false,
                isCbrEnabled = false,
                conversationName = null,
                conversationType = Conversation.Type.Group.Regular,
                callerName = null,
                callerTeamName = null,
                establishedTime = null
            )
        }
    }
}
