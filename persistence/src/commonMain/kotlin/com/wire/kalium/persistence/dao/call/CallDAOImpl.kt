package com.wire.kalium.persistence.dao.call

import app.cash.sqldelight.coroutines.asFlow
import com.wire.kalium.persistence.CallsQueries
import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.util.mapToList
import com.wire.kalium.persistence.util.mapToOneOrNull
import com.wire.kalium.util.DateTimeUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import com.wire.kalium.persistence.Call as SQLDelightCall

internal object CallMapper {
    fun toModel(dbEntry: SQLDelightCall) = CallEntity(
        conversationId = dbEntry.conversation_id,
        id = dbEntry.id,
        status = dbEntry.status,
        callerId = dbEntry.caller_id,
        conversationType = dbEntry.conversation_type
    )

    @Suppress("FunctionParameterNaming", "LongParameterList", "UNUSED_PARAMETER")
    fun fromCalls(
        conversationId: QualifiedIDEntity,
        id: String,
        status: CallEntity.Status,
        callerId: String,
        conversationType: ConversationEntity.Type,
        createdAt: String,
    ): CallEntity = CallEntity(
        conversationId = conversationId,
        id = id,
        status = status,
        callerId = callerId,
        conversationType = conversationType
    )
}

internal class CallDAOImpl(
    private val callsQueries: CallsQueries,
    private val queriesContext: CoroutineContext,
    private val mapper: CallMapper = CallMapper
) : CallDAO {

    override suspend fun insertCall(call: CallEntity) = withContext(queriesContext) {
        val createdTime: Long = DateTimeUtil.currentInstant().toEpochMilliseconds()

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
            .flowOn(queriesContext)
            .mapToList()

    override suspend fun observeIncomingCalls(): Flow<List<CallEntity>> =
        callsQueries.selectIncomingCalls(mapper = mapper::fromCalls)
            .asFlow()
            .flowOn(queriesContext)
            .mapToList()

    override suspend fun observeEstablishedCalls(): Flow<List<CallEntity>> =
        callsQueries.selectEstablishedCalls(mapper = mapper::fromCalls)
            .asFlow()
            .flowOn(queriesContext)
            .mapToList()

    override suspend fun observeOngoingCalls(): Flow<List<CallEntity>> =
        callsQueries.selectOngoingCalls(mapper = mapper::fromCalls)
            .asFlow()
            .flowOn(queriesContext)
            .mapToList()

    override suspend fun updateLastCallStatusByConversationId(status: CallEntity.Status, conversationId: QualifiedIDEntity) =
        withContext(queriesContext) {
            callsQueries.updateLastCallStatusByConversationId(
                status,
                conversationId
            )
        }

    override suspend fun getCallerIdByConversationId(conversationId: QualifiedIDEntity): String = withContext(queriesContext) {
        callsQueries.lastCallCallerIdByConversationId(conversationId).executeAsOne()
    }

    override suspend fun getCallStatusByConversationId(conversationId: QualifiedIDEntity): CallEntity.Status? =
        withContext(queriesContext) {
            callsQueries.lastCallStatusByConversationId(conversationId).executeAsOneOrNull()
        }

    override suspend fun getLastClosedCallByConversationId(conversationId: QualifiedIDEntity): Flow<String?> =
        callsQueries.selectLastClosedCallCreationTimeConversationId(conversationId)
            .asFlow()
            .flowOn(queriesContext)
            .mapToOneOrNull()

    override suspend fun getLastCallConversationTypeByConversationId(
        conversationId: QualifiedIDEntity
    ): ConversationEntity.Type? = withContext(queriesContext) {
        callsQueries.selectLastCallConversionTypeByConversationId(conversationId)
            .executeAsOneOrNull()
    }

    override suspend fun updateOpenCallsToClosedStatus() = withContext(queriesContext) {
        callsQueries.updateOpenCallsToClosedStatus()
    }
}
