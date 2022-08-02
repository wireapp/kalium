package com.wire.kalium.persistence.dao.call

import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOne
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import com.wire.kalium.persistence.CallsQueries
import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import com.wire.kalium.persistence.Call as SQLDelightCall

internal class CallMapper {
    fun toModel(dbEntry: SQLDelightCall) = CallEntity(
        conversationId = dbEntry.conversation_id,
        id = dbEntry.id,
        status = dbEntry.status,
        callerId = dbEntry.caller_id,
        conversationType = dbEntry.conversation_type
    )
}

internal class CallDAOImpl(private val callsQueries: CallsQueries) : CallDAO {
    val mapper = CallMapper()

    override suspend fun insertCall(call: CallEntity) {
        val createdTime: Long = Clock.System.now().toEpochMilliseconds()

        callsQueries.insertCall(
            conversation_id = call.conversationId,
            id = call.id,
            status = call.status,
            caller_id = call.callerId,
            conversation_type = call.conversationType,
            created_at = createdTime.toString()
        )
    }

    override suspend fun observeCalls(): Flow<List<CallEntity>> =
        callsQueries.selectAllCalls()
            .asFlow()
            .mapToList()
            .map { calls -> calls.map(mapper::toModel) }

    override suspend fun observeIncomingCalls(): Flow<List<CallEntity>> =
        callsQueries.selectIncomingCalls()
            .asFlow()
            .mapToList()
            .map { calls -> calls.map(mapper::toModel) }

    override suspend fun observeEstablishedCalls(): Flow<List<CallEntity>> =
        callsQueries.selectEstablishedCalls()
            .asFlow()
            .mapToList()
            .map { calls -> calls.map(mapper::toModel) }

    override suspend fun observeOngoingCalls(): Flow<List<CallEntity>> =
        callsQueries.selectOngoingCalls()
            .asFlow()
            .mapToList()
            .map { calls -> calls.map(mapper::toModel) }

    override suspend fun updateLastCallStatusByConversationId(status: CallEntity.Status, conversationId: QualifiedIDEntity) {
        callsQueries.updateLastCallStatusByConversationId(
            status,
            conversationId
        )
    }

    override suspend fun getCallerIdByConversationId(conversationId: QualifiedIDEntity): String =
        callsQueries.selectLastCallByConversationId(conversationId)
            .asFlow()
            .mapToOne()
            .map { it.caller_id }
            .first()

    override suspend fun getCallStatusByConversationId(conversationId: QualifiedIDEntity): CallEntity.Status? =
        callsQueries.selectLastCallByConversationId(conversationId)
            .asFlow()
            .mapToOneOrNull()
            .map { call ->
                call?.let {
                    mapper.toModel(dbEntry = it).status
                }
            }.firstOrNull()

    override suspend fun getLastClosedCallByConversationId(conversationId: QualifiedIDEntity): Flow<String?> =
        callsQueries.selectLastClosedCallByConversationId(conversationId)
            .asFlow()
            .mapToOneOrNull()
            .map { it?.created_at }

    override suspend fun getLastCallConversationTypeByConversationId(conversationId: QualifiedIDEntity): ConversationEntity.Type? =
        callsQueries.selectLastCallByConversationId(conversationId)
            .asFlow()
            .mapToOneOrNull()
            .map { call ->
                call?.conversation_type
            }.firstOrNull()
}
