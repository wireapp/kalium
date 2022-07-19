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
    suspend fun observeCalls(): Flow<List<CallEntity>>
    suspend fun observeIncomingCalls(): Flow<List<CallEntity>>
    suspend fun observeEstablishedCalls(): Flow<List<CallEntity>>
    suspend fun observeOngoingCalls(): Flow<List<CallEntity>>
    suspend fun updateLastCallStatusByConversationId(status: CallEntity.Status, conversationId: QualifiedIDEntity)
    suspend fun getCallerIdByConversationId(conversationId: QualifiedIDEntity): String
    suspend fun getCallStatusByConversationId(conversationId: QualifiedIDEntity): CallEntity.Status?
    suspend fun deleteAllCalls()
    suspend fun getLastClosedCallByConversationId(conversationId: QualifiedIDEntity): Flow<String?>
}
