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

package com.wire.kalium.persistence.dao.message

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOne
import com.wire.kalium.persistence.MessagesToSynchronizeQueries
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.db.ReadDispatcher
import com.wire.kalium.persistence.db.WriteDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant

internal class MessageSyncDAOImpl(
    private val queries: MessagesToSynchronizeQueries,
    private val readDispatcher: ReadDispatcher,
    private val writeDispatcher: WriteDispatcher
) : MessageSyncDAO {

    override suspend fun insertOrReplaceMessageToSync(
        conversationId: QualifiedIDEntity,
        messageNonce: String,
        timestamp: Instant,
        operation: SyncOperationType,
        payload: String?
    ): Unit = withContext(writeDispatcher.value) {
        queries.insertOrReplaceMessage(
            conversation_id = conversationId,
            message_nonce = messageNonce,
            timestamp = timestamp,
            operation = operation.value.toLong(),
            payload = payload
        )
    }

    override suspend fun getMessagesToSync(limit: Int): List<MessageToSynchronizeEntity> =
        withContext(readDispatcher.value) {
            queries.getMessagesToSync(limit.toLong())
                .executeAsList()
                .map { 
                    MessageToSynchronizeEntity(
                        conversationId = it.conversation_id,
                        messageNonce = it.message_nonce,
                        timestamp = it.timestamp,
                        operation = SyncOperationType.fromInt(it.operation.toInt()),
                        payload = it.payload
                    )
                }
        }

    override suspend fun deleteSyncedMessages(
        conversationIds: List<QualifiedIDEntity>,
        messageNonces: List<String>
    ): Unit = withContext(writeDispatcher.value) {
        queries.deleteSyncedBatch(conversationIds, messageNonces)
    }

    override suspend fun countPendingMessages(): Long =
        withContext(readDispatcher.value) {
            queries.countPendingMessages().executeAsOne()
        }

    override suspend fun getAllMessages(): List<MessageToSynchronizeEntity> =
        withContext(readDispatcher.value) {
            queries.getAllMessages()
                .executeAsList()
                .map {
                    MessageToSynchronizeEntity(
                        conversationId = it.conversation_id,
                        messageNonce = it.message_nonce,
                        timestamp = it.timestamp,
                        operation = SyncOperationType.fromInt(it.operation.toInt()),
                        payload = it.payload
                    )
                }
        }

    override fun observePendingMessagesCount(): Flow<Long> =
        queries.countPendingMessages()
            .asFlow()
            .mapToOne(readDispatcher.value)
}
