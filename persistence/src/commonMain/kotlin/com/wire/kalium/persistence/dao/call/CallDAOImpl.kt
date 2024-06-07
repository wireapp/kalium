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

import app.cash.sqldelight.coroutines.asFlow
import com.wire.kalium.persistence.CallsQueries
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
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
        conversationType = dbEntry.conversation_type,
        type = dbEntry.type
    )

    @Suppress("FunctionParameterNaming", "LongParameterList", "UNUSED_PARAMETER")
    fun fromCalls(
        conversationId: QualifiedIDEntity,
        id: String,
        status: CallEntity.Status,
        callerId: String,
        conversationType: ConversationEntity.Type,
        createdAt: String,
        type: CallEntity.Type
    ): CallEntity = CallEntity(
        conversationId = conversationId,
        id = id,
        status = status,
        callerId = callerId,
        conversationType = conversationType,
        type = type
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
            created_at = createdTime.toString(),
            type = call.type
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

    override suspend fun observeOutgoingCalls(): Flow<List<CallEntity>> =
        callsQueries.selectOutgoingCalls(mapper = mapper::fromCalls)
            .asFlow()
            .flowOn(queriesContext)
            .mapToList()

    override suspend fun observeEstablishedCalls(): Flow<List<CallEntity>> =
        callsQueries.selectEstablishedCalls(mapper = mapper::fromCalls)
            .asFlow()
            .flowOn(queriesContext)
            .mapToList()

    override fun getEstablishedCall(): CallEntity =
        callsQueries.selectEstablishedCalls(mapper = mapper::fromCalls).executeAsOne()

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

    override suspend fun getCallerIdByConversationId(conversationId: QualifiedIDEntity): String? = withContext(queriesContext) {
        callsQueries.lastCallCallerIdByConversationId(conversationId).executeAsOneOrNull()
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
