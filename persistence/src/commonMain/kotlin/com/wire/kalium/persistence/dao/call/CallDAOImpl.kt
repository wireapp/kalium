package com.wire.kalium.persistence.dao.call

import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOne
import com.wire.kalium.persistence.CallsQueries
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
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
        val createdTime: Long = Clock.System.now().epochSeconds

        callsQueries.insertCall(
            conversation_id = call.conversationId,
            id = call.id,
            status = call.status,
            caller_id = call.callerId,
            conversation_type = call.conversationType,
            created_at = createdTime.toString()
        )
    }

    override suspend fun getCalls(): Flow<List<CallEntity>> =
        callsQueries.selectAllCalls()
            .asFlow()
            .mapToList()
            .map { calls -> calls.map(mapper::toModel) }

    override suspend fun getIncomingCalls(): Flow<List<CallEntity>> =
        callsQueries.selectIncomingCalls()
            .asFlow()
            .mapToList()
            .map { calls -> calls.map(mapper::toModel) }

    override suspend fun getEstablishedCalls(): Flow<List<CallEntity>> =
        callsQueries.selectEstablishedCalls()
            .asFlow()
            .mapToList()
            .map { calls -> calls.map(mapper::toModel) }

    override suspend fun getOngoingCalls(): Flow<List<CallEntity>> =
        callsQueries.selectOngoingCalls()
            .asFlow()
            .mapToList()
            .map { calls -> calls.map(mapper::toModel) }

    override suspend fun isOngoingCall(conversationId: QualifiedIDEntity): Boolean =
        callsQueries.isOngoingCall(conversationId)
            .asFlow()
            .mapToList()
            .map { it.isEmpty() }
            .first()

    override suspend fun updateLastCallStatusByConversationId(status: CallEntity.Status, conversationId: QualifiedIDEntity) {
        callsQueries.updateLastCallStatusByConversationId(
            status,
            conversationId
        )
    }

    override suspend fun getCallerIdByConversationId(conversationId: QualifiedIDEntity): String =
        callsQueries.selectLastCallCallerIdByConversationId(conversationId)
            .asFlow()
            .mapToOne()
            .map { it }
            .first()
}

