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

import com.wire.kalium.persistence.RemotebackupChangeLogQueries
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.db.ReadDispatcher
import com.wire.kalium.persistence.db.WriteDispatcher
import kotlinx.coroutines.withContext

internal class RemoteBackupChangeLogDAOImpl(
    private val queries: RemotebackupChangeLogQueries,
    private val readDispatcher: ReadDispatcher,
    private val writeDispatcher: WriteDispatcher,
) : RemoteBackupChangeLogDAO {

    override suspend fun logMessageUpsert(
        conversationId: QualifiedIDEntity,
        messageId: String,
        timestampMs: Long
    ): Unit = withContext(writeDispatcher.value) {
        queries.insertMessageUpsert(
            conversationId = conversationId,
            messageId = messageId,
            eventType = ChangeLogEventType.MESSAGE_UPSERT,
            timestampMs = timestampMs
        )
    }

    override suspend fun logMessageDelete(
        conversationId: QualifiedIDEntity,
        messageId: String,
        timestampMs: Long
    ): Unit = withContext(writeDispatcher.value) {
        queries.insertMessageDelete(
            conversationId = conversationId,
            messageId = messageId,
            eventType = ChangeLogEventType.MESSAGE_DELETE,
            timestampMs = timestampMs
        )
    }

    override suspend fun logReactionsSync(
        conversationId: QualifiedIDEntity,
        messageId: String,
        timestampMs: Long
    ): Unit = withContext(writeDispatcher.value) {
        queries.insertReactionsSync(
            conversationId = conversationId,
            messageId = messageId,
            eventType = ChangeLogEventType.REACTIONS_SYNC,
            timestampMs = timestampMs
        )
    }

    override suspend fun logReadReceiptsSync(
        conversationId: QualifiedIDEntity,
        messageId: String,
        timestampMs: Long
    ): Unit = withContext(writeDispatcher.value) {
        queries.insertReadReceiptsSync(
            conversationId = conversationId,
            messageId = messageId,
            eventType = ChangeLogEventType.READ_RECEIPT_SYNC,
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

    override suspend fun getPendingChanges(): List<ChangeLogEntry> =
        withContext(readDispatcher.value) {
            queries.getPendingChanges(mapper = ::toChangeLogEntry).executeAsList()
        }

    @Suppress("FunctionParameterNaming")
    private fun toChangeLogEntry(
        conversation_id: QualifiedIDEntity,
        message_id: String?,
        event_type: ChangeLogEventType,
        timestamp_ms: Long,
    ): ChangeLogEntry =
        ChangeLogEntry(
            conversationId = conversation_id,
            messageId = message_id,
            eventType = event_type,
            timestampMs = timestamp_ms
        )
}
