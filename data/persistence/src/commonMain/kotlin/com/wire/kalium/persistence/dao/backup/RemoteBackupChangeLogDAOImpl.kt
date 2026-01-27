/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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

package com.wire.kalium.persistence.dao.backup

import app.cash.sqldelight.coroutines.asFlow
import com.wire.kalium.persistence.RemotebackupChangeLogQueries
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.db.ReadDispatcher
import com.wire.kalium.persistence.db.WriteDispatcher
import com.wire.kalium.persistence.util.mapToList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal class RemoteBackupChangeLogDAOImpl(
    private val queries: RemotebackupChangeLogQueries,
    private val readDispatcher: ReadDispatcher,
    private val writeDispatcher: WriteDispatcher,
) : RemoteBackupChangeLogDAO {

    override suspend fun logMessageUpsert(
        conversationId: QualifiedIDEntity,
        messageNonce: String,
        timestampMs: Long
    ): Unit = withContext(writeDispatcher.value) {
        queries.insertMessageUpsert(
            conversationId = conversationId,
            messageNonce = messageNonce,
            eventType = ChangeLogEventType.MESSAGE_UPSERT,
            timestampMs = timestampMs
        )
    }

    override suspend fun logMessageDelete(
        conversationId: QualifiedIDEntity,
        messageNonce: String,
        timestampMs: Long
    ): Unit = withContext(writeDispatcher.value) {
        queries.insertMessageDelete(
            conversationId = conversationId,
            messageNonce = messageNonce,
            eventType = ChangeLogEventType.MESSAGE_DELETE,
            timestampMs = timestampMs
        )
    }

    override suspend fun logReactionsSync(
        conversationId: QualifiedIDEntity,
        messageNonce: String,
        timestampMs: Long
    ): Unit = withContext(writeDispatcher.value) {
        queries.insertReactionsSync(
            conversationId = conversationId,
            messageNonce = messageNonce,
            eventType = ChangeLogEventType.REACTIONS_SYNC,
            timestampMs = timestampMs
        )
    }

    override suspend fun logConversationDelete(
        conversationId: QualifiedIDEntity,
        timestampMs: Long
    ): Unit = withContext(writeDispatcher.value) {
        queries.insertConversationDelete(
            conversationId = conversationId,
            eventType = ChangeLogEventType.CONVERSATION_DELETE,
            timestampMs = timestampMs
        )
    }

    override suspend fun logConversationClear(
        conversationId: QualifiedIDEntity,
        timestampMs: Long
    ): Unit = withContext(writeDispatcher.value) {
        queries.insertConversationClear(
            conversationId = conversationId,
            eventType = ChangeLogEventType.CONVERSATION_CLEAR,
            timestampMs = timestampMs
        )
    }

    override fun observePendingChanges(): Flow<List<ChangeLogEntry>> =
        queries.getPendingChanges()
            .asFlow()
            .flowOn(readDispatcher.value)
            .mapToList()
            .map { list -> list.map { it.toChangeLogEntry() } }

    override suspend fun getPendingChanges(): List<ChangeLogEntry> =
        withContext(readDispatcher.value) {
            queries.getPendingChanges().executeAsList().map { it.toChangeLogEntry() }
        }

    override suspend fun getChangesByEventType(eventType: ChangeLogEventType): List<ChangeLogEntry> =
        withContext(readDispatcher.value) {
            queries.getChangesByEventType(eventType)
                .executeAsList()
                .map { it.toChangeLogEntry() }
        }

    override suspend fun getChangesForConversation(conversationId: QualifiedIDEntity): List<ChangeLogEntry> =
        withContext(readDispatcher.value) {
            queries.getChangesForConversation(conversationId)
                .executeAsList()
                .map { it.toChangeLogEntry() }
        }

    override suspend fun deleteProcessedEntries(upToTimestampMs: Long): Unit =
        withContext(writeDispatcher.value) {
            queries.deleteProcessedEntries(upToTimestampMs)
        }

    override suspend fun deleteEntry(
        conversationId: QualifiedIDEntity,
        messageNonce: String,
        eventType: ChangeLogEventType
    ): Unit = withContext(writeDispatcher.value) {
        queries.deleteEntry(
            conversation_id = conversationId,
            message_nonce = messageNonce,
            event_type = eventType
        )
    }

    private fun com.wire.kalium.persistence.RemotebackupChangeLog.toChangeLogEntry(): ChangeLogEntry =
        ChangeLogEntry(
            conversationId = conversation_id,
            messageNonce = message_nonce,
            eventType = event_type,
            timestampMs = timestamp_ms
        )
}
