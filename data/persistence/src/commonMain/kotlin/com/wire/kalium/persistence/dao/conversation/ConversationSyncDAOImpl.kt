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

package com.wire.kalium.persistence.dao.conversation

import com.wire.kalium.persistence.ConversationsSynchronizationQueries
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.db.ReadDispatcher
import com.wire.kalium.persistence.db.WriteDispatcher
import kotlinx.coroutines.withContext

internal class ConversationSyncDAOImpl(
    private val queries: ConversationsSynchronizationQueries,
    private val readDispatcher: ReadDispatcher,
    private val writeDispatcher: WriteDispatcher
) : ConversationSyncDAO {

    override suspend fun upsertConversationSync(
        conversationId: QualifiedIDEntity,
        lastReadTimestamp: Long
    ): Unit = withContext(writeDispatcher.value) {
        queries.upsertConversationSync(
            conversationId = conversationId,
            lastReadTimestamp = lastReadTimestamp
        )
    }

    override suspend fun getConversationsWithPendingSync(): List<ConversationPendingSyncEntity> =
        withContext(readDispatcher.value) {
            queries.getConversationsWithPendingSync()
                .executeAsList()
                .map {
                    ConversationPendingSyncEntity(
                        conversationId = it.conversation_id,
                        toUploadLastRead = it.to_upload_last_read!!
                    )
                }
        }

    override suspend fun markAsUploaded(conversationId: QualifiedIDEntity): Unit =
        withContext(writeDispatcher.value) {
            queries.markAsUploaded(conversationId)
        }

    override suspend fun getSyncStatusForConversation(conversationId: QualifiedIDEntity): ConversationSyncEntity? =
        withContext(readDispatcher.value) {
            queries.getSyncStatusForConversation(conversationId)
                .executeAsOneOrNull()
                ?.let {
                    ConversationSyncEntity(
                        conversationId = it.conversation_id,
                        lastUploadedLastRead = it.last_uploaded_last_read,
                        toUploadLastRead = it.to_upload_last_read
                    )
                }
        }

    override suspend fun countPendingSync(): Long =
        withContext(readDispatcher.value) {
            queries.countPendingSync().executeAsOne()
        }

    override suspend fun getAllConversationsSync(): List<ConversationSyncEntity> =
        withContext(readDispatcher.value) {
            queries.getAllConversationsSync()
                .executeAsList()
                .map {
                    ConversationSyncEntity(
                        conversationId = it.conversation_id,
                        lastUploadedLastRead = it.last_uploaded_last_read,
                        toUploadLastRead = it.to_upload_last_read
                    )
                }
        }
}
