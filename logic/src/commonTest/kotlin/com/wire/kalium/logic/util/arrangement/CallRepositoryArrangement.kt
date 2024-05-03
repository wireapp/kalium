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
package com.wire.kalium.logic.util.arrangement

import com.wire.kalium.logic.data.call.Call
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.CallStatus
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import io.mockative.Mock
import io.mockative.coEvery
import io.mockative.mock
import kotlinx.coroutines.flow.flowOf

interface CallRepositoryArrangement {
    val callRepository: CallRepository
    suspend fun withEstablishedCall()
    suspend fun withoutAnyEstablishedCall()
}

internal class CallRepositoryArrangementImpl : CallRepositoryArrangement {

    @Mock
    override val callRepository = mock(CallRepository::class)

    override suspend fun withEstablishedCall() {
        coEvery {
            callRepository.establishedCallsFlow()
        }.returns(flowOf(listOf(call)))
    }

    override suspend fun withoutAnyEstablishedCall() {
        coEvery {
            callRepository.establishedCallsFlow()
        }.returns(flowOf(listOf()))
    }

    companion object {
        val call = Call(
            conversationId = ConversationId("conversationId", "domain"),
            status = CallStatus.ESTABLISHED,
            callerId = UserId("caller", "domain").toString(),
            participants = listOf(),
            isMuted = true,
            isCameraOn = false,
            isCbrEnabled = false,
            maxParticipants = 0,
            conversationName = "ONE_ON_ONE Name",
            conversationType = Conversation.Type.ONE_ON_ONE,
            callerName = "otherUsername",
            callerTeamName = "team_1"
        )
    }
}
