package com.wire.kalium.persistence.dao.call

import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import kotlinx.coroutines.flow.Flow



data class CallEntity(
    val conversationId: QualifiedIDEntity,
    val id: String,
    val status: Status,
    val callerId: String,
    val conversationType: ConversationEntity.Type
) {
    enum class Status {
        STARTED,
        INCOMING,
        MISSED,
        ANSWERED,
        ESTABLISHED,
        STILL_ONGOING,
        CLOSED
    }
}

interface CallDAO {
    suspend fun insertCall(call: CallEntity)
    suspend fun getCalls(): Flow<List<CallEntity>>
    suspend fun getIncomingCalls(): Flow<List<CallEntity>>
    suspend fun getEstablishedCalls(): Flow<List<CallEntity>>
    suspend fun getOngoingCalls(): Flow<List<CallEntity>>
    suspend fun isOngoingCall(conversationId: QualifiedIDEntity): Boolean
    suspend fun updateLastCallStatusByConversationId(status: CallEntity.Status, conversationId: QualifiedIDEntity)
    suspend fun getCallerIdByConversationId(conversationId: QualifiedIDEntity): String
}
