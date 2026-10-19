/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.persistence.dao.conversation

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.wire.kalium.persistence.UserDatabase
import com.wire.kalium.persistence.db.ReadDispatcher
import com.wire.kalium.persistence.db.WriteDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Instant

internal class PendingSubconversationProposalDAOImpl(
    private val database: UserDatabase,
    private val readDispatcher: ReadDispatcher,
    private val writeDispatcher: WriteDispatcher,
) : PendingSubconversationProposalDAO {
    private val queries get() = database.pendingSubconversationProposalQueries

    override suspend fun schedule(record: PendingSubconversationProposalEntity) = withContext(writeDispatcher.value) {
        database.transaction {
            importLegacyProposals()
            val existing = selectCurrent(record.groupId)
            val deadline = minOf(existing?.deadline ?: record.deadline, record.deadline)
            queries.upsert(record.groupId, deadline.toString(), record.token, 0)
        }
    }

    override fun observe(): Flow<List<PendingSubconversationProposalEntity>> = flow {
        withContext(writeDispatcher.value) { database.transaction { importLegacyProposals() } }
        emitAll(queries.selectAll(::toEntity).asFlow().mapToList(readDispatcher.value).flowOn(readDispatcher.value))
    }

    override suspend fun current(groupId: String): PendingSubconversationProposalEntity? = withContext(writeDispatcher.value) {
        database.transactionWithResult {
            importLegacyProposals()
            selectCurrent(groupId)
        }
    }

    override suspend fun isCurrent(record: PendingSubconversationProposalEntity): Boolean = current(record.groupId) == record

    override suspend fun markForRetirement(record: PendingSubconversationProposalEntity): PendingSubconversationProposalEntity? =
        withContext(writeDispatcher.value) {
            database.transactionWithResult {
                importLegacyProposals()
                if (selectCurrent(record.groupId) != record) return@transactionWithResult null
                queries.markForRetirement(record.groupId, record.deadline.toString(), record.token, record.leaveRequested.toSql())
                selectCurrent(record.groupId)
            }
        }

    override suspend fun acknowledge(record: PendingSubconversationProposalEntity) = withContext(writeDispatcher.value) {
        database.transaction {
            importLegacyProposals()
            queries.acknowledge(record.groupId, record.deadline.toString(), record.token, record.leaveRequested.toSql())
        }
    }

    override suspend fun clear() = withContext(writeDispatcher.value) {
        database.transaction {
            queries.clear()
            database.metadataQueries.deleteValue(LEGACY_KEY)
        }
    }

    private suspend fun selectCurrent(groupId: String): PendingSubconversationProposalEntity? =
        queries.selectCurrent(groupId, ::toEntity).awaitAsOneOrNull()

    /** Run inside the caller's SQL transaction. Conflicts or failures preserve all legacy work for retry. */
    private suspend fun importLegacyProposals() {
        val serialized = database.metadataQueries.selectValueByKey(LEGACY_KEY).awaitAsOneOrNull() ?: return
        val records = Json.decodeFromString<List<LegacyProposal>>(serialized).map {
            PendingSubconversationProposalEntity(it.group, Instant.parse(it.deadline), it.token, it.leaveRequested)
        }
        check(records.distinctBy { it.groupId }.size == records.size) { "Duplicate legacy subgroup proposal" }
        records.forEach { record ->
            val existing = selectCurrent(record.groupId)
            check(existing == null || existing == record) { "Conflicting legacy subgroup proposal" }
            if (existing == null) {
                queries.upsert(record.groupId, record.deadline.toString(), record.token, record.leaveRequested.toSql())
            }
        }
        database.metadataQueries.deleteValue(LEGACY_KEY)
    }

    @Serializable
    private data class LegacyProposal(
        @SerialName("group") val group: String,
        @SerialName("deadline") val deadline: String,
        @SerialName("token") val token: String,
        @SerialName("leaveRequested") val leaveRequested: Boolean = false,
    )

    private companion object {
        const val LEGACY_KEY = "pending-subconversation-proposals-v1"

        fun Boolean.toSql(): Long = if (this) 1 else 0

        fun toEntity(groupId: String, deadline: String, token: String, leaveRequested: Long) =
            PendingSubconversationProposalEntity(groupId, Instant.parse(deadline), token, leaveRequested != 0L)
    }
}
