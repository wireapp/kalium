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
import io.mockative.Mock
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class UpdateConversationClientsForCurrentCallUseCaseTest {

    @Mock
    private val callRepository = mock(CallRepository::class)

    @Mock
    private val conversationClientsInCallUpdater = mock(ConversationClientsInCallUpdater::class)

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
        coEvery {
            callRepository.establishedCallsFlow()
        }.returns(flowOf(listOf()))

        updateConversationClientsForCurrentCall(CONVERSATION_ID)

        coVerify {
            callRepository.establishedCallsFlow()
        }.wasInvoked(once)

        coVerify {
            conversationClientsInCallUpdater.invoke(eq(CONVERSATION_ID))
        }.wasNotInvoked()
    }

    @Test
    fun givenAnOngoingCall_whenUseCaseIsInvoked_thenUpdateClients() = runTest {
        coEvery {
            callRepository.establishedCallsFlow()
        }.returns(flowOf(listOf(call)))

        updateConversationClientsForCurrentCall(CONVERSATION_ID)

        coVerify {
            callRepository.establishedCallsFlow()
        }.wasInvoked(once)
        coVerify {
            conversationClientsInCallUpdater.invoke(eq(CONVERSATION_ID))
        }.wasInvoked()
    }

    companion object {
        private val CONVERSATION_ID = ConversationId("conversationId", "domain")
        private val call = Call(
            conversationId = CONVERSATION_ID,
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
