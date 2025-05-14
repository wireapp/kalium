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

package com.wire.kalium.persistence.dao.call

import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import io.mockative.Mockable
import kotlinx.coroutines.flow.Flow

data class CallEntity(
    val conversationId: QualifiedIDEntity,
    val id: String,
    val status: Status,
    val callerId: String,
    val conversationType: ConversationEntity.Type,
    val type: Type
) {
    enum class Status {
        STARTED,
        INCOMING,
        MISSED,
        ANSWERED,
        ESTABLISHED,
        STILL_ONGOING,
        CLOSED_INTERNALLY,
        CLOSED,
        REJECTED
    }

    enum class Type {
        ONE_ON_ONE,
        CONFERENCE,
        MLS_CONFERENCE,
        UNKNOWN
    }
}

@Mockable
interface CallDAO {
    suspend fun insertCall(call: CallEntity)
    suspend fun observeCalls(): Flow<List<CallEntity>>
    suspend fun observeIncomingCalls(): Flow<List<CallEntity>>
    suspend fun observeOutgoingCalls(): Flow<List<CallEntity>>
    suspend fun observeEstablishedCalls(): Flow<List<CallEntity>>
    fun getEstablishedCall(): CallEntity
    suspend fun observeOngoingCalls(): Flow<List<CallEntity>>
    suspend fun updateLastCallStatusByConversationId(status: CallEntity.Status, conversationId: QualifiedIDEntity)
    suspend fun getCallerIdByConversationId(conversationId: QualifiedIDEntity): String?
    suspend fun getCallStatusByConversationId(conversationId: QualifiedIDEntity): CallEntity.Status?
    suspend fun getLastClosedCallByConversationId(conversationId: QualifiedIDEntity): Flow<String?>
    suspend fun getLastCallConversationTypeByConversationId(conversationId: QualifiedIDEntity): ConversationEntity.Type?
    suspend fun updateOpenCallsToClosedStatus()
}
