package com.wire.kalium.persistence.dao.call

import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import com.wire.kalium.persistence.CallsQueries
import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock
import com.wire.kalium.persistence.Call as SQLDelightCall

internal object CallMapper {
    fun toModel(dbEntry: SQLDelightCall) = CallEntity(
        conversationId = dbEntry.conversation_id,
        id = dbEntry.id,
        status = dbEntry.status,
        callerId = dbEntry.caller_id,
        conversationType = dbEntry.conversation_type
    )

    @Suppress("FunctionParameterNaming", "LongParameterList")
    fun fromCalls(
        conversation_id: QualifiedIDEntity,
        id: String,
        status: CallEntity.Status,
        caller_id: String,
        conversation_type: ConversationEntity.Type,
        created_at: String,
    ): CallEntity = CallEntity(
        conversationId = conversation_id,
        id = id,
        status = status,
        callerId = caller_id,
        conversationType = conversation_type
    )
}

internal class CallDAOImpl(
    private val callsQueries: CallsQueries,
    private val mapper: CallMapper = CallMapper
) : CallDAO {

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
        callsQueries.selectAllCalls(mapper = mapper::fromCalls)
            .asFlow()
            .mapToList()

    override suspend fun observeIncomingCalls(): Flow<List<CallEntity>> =
        callsQueries.selectIncomingCalls(mapper = mapper::fromCalls)
            .asFlow()
            .mapToList()

    override suspend fun getIncomingCalls(): List<CallEntity> =
        callsQueries.selectIncomingCalls(mapper = mapper::fromCalls)
            .executeAsList()

    override suspend fun observeEstablishedCalls(): Flow<List<CallEntity>> =
        callsQueries.selectEstablishedCalls(mapper = mapper::fromCalls)
            .asFlow()
            .mapToList()

    override suspend fun observeOngoingCalls(): Flow<List<CallEntity>> =
        callsQueries.selectOngoingCalls(mapper = mapper::fromCalls)
            .asFlow()
            .mapToList()

    override suspend fun updateLastCallStatusByConversationId(status: CallEntity.Status, conversationId: QualifiedIDEntity) {
        callsQueries.updateLastCallStatusByConversationId(
            status,
            conversationId
        )
    }

    override suspend fun getCallerIdByConversationId(conversationId: QualifiedIDEntity): String =
        callsQueries.lastCallCallerIdByConversationId(conversationId).executeAsOne()

    override suspend fun getCallStatusByConversationId(conversationId: QualifiedIDEntity): CallEntity.Status? =
        callsQueries.lastCallStatusByConversationId(conversationId).executeAsOneOrNull()

    override suspend fun getLastClosedCallByConversationId(conversationId: QualifiedIDEntity): Flow<String?> =
        callsQueries.selectLastClosedCallCreationTimeConversationId(conversationId)
            .asFlow()
            .mapToOneOrNull()

    override suspend fun getLastCallConversationTypeByConversationId(
        conversationId: QualifiedIDEntity
    ): ConversationEntity.Type? =
        callsQueries.selectLastCallConversionTypeByConversationId(conversationId)
            .executeAsOneOrNull()

    override suspend fun updateOpenCallsToClosedStatus() {
        callsQueries.updateOpenCallsToClosedStatus()
    }
}
