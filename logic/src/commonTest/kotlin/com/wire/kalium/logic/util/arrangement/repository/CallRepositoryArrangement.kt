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
package com.wire.kalium.logic.util.arrangement.repository

import com.wire.kalium.logic.data.call.Call
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.CallStatus
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestCall
import dev.mokkery.matcher.any
import dev.mokkery.everySuspend
import dev.mokkery.every
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.mock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal interface CallRepositoryArrangement {
    val callRepository: CallRepository

    suspend fun withEstablishedCallsFlow(calls: List<Call>)
    suspend fun withEstablishedCall()
    suspend fun withoutAnyEstablishedCall()
    suspend fun withCallsFlow(flow: Flow<List<Call>>)
    suspend fun withUpdateIsCameraOnById(
        conversationId: ConversationId = ConversationId("conversationId", "domain"),
        isCameraOn: Boolean = false
    )
}

internal open class CallRepositoryArrangementImpl : CallRepositoryArrangement {

    override val callRepository: CallRepository = mock<CallRepository>(mode = MockMode.autoUnit)

    override suspend fun withEstablishedCallsFlow(calls: List<Call>) {
        everySuspend {
            callRepository.establishedCallsFlow()
        }.returns(flowOf(calls))
    }

    override suspend fun withEstablishedCall() {
        everySuspend {
            callRepository.establishedCallsFlow()
        }.returns(flowOf(listOf(CallRepositoryArrangementImpl.call)))
    }

    override suspend fun withoutAnyEstablishedCall() {
        everySuspend {
            callRepository.establishedCallsFlow()
        }.returns(flowOf(listOf()))
    }

    override suspend fun withCallsFlow(flow: Flow<List<Call>>) {
        everySuspend { callRepository.callsFlow() }.returns(flow)
    }

    override suspend fun withUpdateIsCameraOnById(conversationId: ConversationId, isCameraOn: Boolean) {
        every { callRepository.updateIsCameraOnById(conversationId, isCameraOn) }.returns(Unit)
    }

    companion object {
        val call = Call(
            conversationId = ConversationId("conversationId", "domain"),
            status = CallStatus.ESTABLISHED,
            callerId = TestCall.CALLER_ID,
            participants = listOf(),
            isMuted = true,
            isCameraOn = false,
            isCbrEnabled = false,
            maxParticipants = 0,
            conversationName = "ONE_ON_ONE Name",
            conversationType = Conversation.Type.OneOnOne,
            callerName = "otherUsername",
            callerTeamName = "team_1"
        )
    }
}
