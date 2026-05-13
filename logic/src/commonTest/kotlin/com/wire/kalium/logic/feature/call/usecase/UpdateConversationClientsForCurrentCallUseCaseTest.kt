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
import com.wire.kalium.logic.framework.TestCall
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.verify.VerifyMode
import dev.mokkery.everySuspend
import dev.mokkery.verifySuspend
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class UpdateConversationClientsForCurrentCallUseCaseTest {

        private val callRepository = mock<CallRepository>(mode = MockMode.autoUnit)

        private val conversationClientsInCallUpdater = mock<ConversationClientsInCallUpdater>(mode = MockMode.autoUnit)

    private lateinit var updateConversationClientsForCurrentCall: UpdateConversationClientsForCurrentCallUseCase

    @BeforeTest
    fun setup() {
        updateConversationClientsForCurrentCall = UpdateConversationClientsForCurrentCallUseCaseImpl(
            callRepository = callRepository,
            conversationClientsInCallUpdater = conversationClientsInCallUpdater
        )
    }

    @Test
    fun givenNoOngoingCall_whenUseCaseIsInvoked_thenNothingToDo() = runTest {
        everySuspend {
            callRepository.establishedCallsFlow()
        } returns (flowOf(listOf()))

        updateConversationClientsForCurrentCall(CONVERSATION_ID)

        verifySuspend(VerifyMode.exactly(1)) {
            callRepository.establishedCallsFlow()
        }

        verifySuspend(VerifyMode.not) {
            conversationClientsInCallUpdater.invoke(eq(CONVERSATION_ID))
        }
    }

    @Test
    fun givenAnOngoingCall_whenUseCaseIsInvoked_thenUpdateClients() = runTest {
        everySuspend {
            callRepository.establishedCallsFlow()
        } returns (flowOf(listOf(call)))

        updateConversationClientsForCurrentCall(CONVERSATION_ID)

        verifySuspend(VerifyMode.exactly(1)) {
            callRepository.establishedCallsFlow()
        }
        verifySuspend {
            conversationClientsInCallUpdater.invoke(eq(CONVERSATION_ID))
        }
    }

    companion object {
        private val CONVERSATION_ID = ConversationId("conversationId", "domain")
        private val call = Call(
            conversationId = CONVERSATION_ID,
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
