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
package com.wire.kalium.logic.data.message.draft

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.persistence.dao.message.draft.MessageDraftDAO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal interface MessageDraftRepository {

    suspend fun getMessageDraft(conversationId: ConversationId): MessageDraft?
    suspend fun saveMessageDraft(messageDraft: MessageDraft): Either<StorageFailure, Unit>
    suspend fun removeMessageDraft(conversationId: ConversationId)
    suspend fun plainTextRevision(conversationId: ConversationId, text: String): Long?
    suspend fun isPlainTextConsumed(conversationId: ConversationId, revision: Long): Boolean
    suspend fun consumePlainText(conversationId: ConversationId, revision: Long)
}

internal class MessageDraftDataSource internal constructor(
    private val messageDraftDAO: MessageDraftDAO,
) : MessageDraftRepository {

    private val mutex = Mutex()
    private val revisions = mutableMapOf<ConversationId, Long>()
    private val consumedRevisions = mutableMapOf<ConversationId, Long>()

    override suspend fun getMessageDraft(conversationId: ConversationId): MessageDraft? = mutex.withLock {
        if (consumed(conversationId, revisions[conversationId] ?: 0L)) null else readDraft(conversationId)
    }

    private suspend fun readDraft(conversationId: ConversationId): MessageDraft? = wrapStorageRequest {
        messageDraftDAO.getMessageDraft(conversationId.toDao())?.toModel()
    }.getOrNull()

    override suspend fun saveMessageDraft(messageDraft: MessageDraft) = wrapStorageRequest {
        mutex.withLock {
            messageDraftDAO.upsertMessageDraft(messageDraft.toDao())
            advanceRevision(messageDraft.conversationId)
        }
    }

    override suspend fun plainTextRevision(conversationId: ConversationId, text: String): Long? = mutex.withLock {
        val draft = readDraft(conversationId) ?: return@withLock null
        if (consumed(conversationId, revisions[conversationId] ?: 0L)) return@withLock null
        val plain = draft.editMessageId == null && draft.quotedMessageId == null && draft.selectedMentionList.isEmpty()
        if (plain && draft.text == text) revisions[conversationId] ?: 0L else null
    }

    override suspend fun isPlainTextConsumed(conversationId: ConversationId, revision: Long): Boolean = mutex.withLock {
        consumed(conversationId, revision)
    }

    private fun consumed(conversationId: ConversationId, revision: Long): Boolean =
        consumedRevisions[conversationId]?.let { revision <= it } ?: false

    override suspend fun consumePlainText(conversationId: ConversationId, revision: Long) {
        wrapStorageRequest {
            mutex.withLock {
                consumedRevisions[conversationId] = maxOf(consumedRevisions[conversationId] ?: -1L, revision)
                if ((revisions[conversationId] ?: 0L) == revision) {
                    messageDraftDAO.removeMessageDraft(conversationId.toDao())
                    advanceRevision(conversationId)
                }
            }
        }.onFailure { kaliumLogger.e("Failed to consume the accepted message draft: $it") }
    }

    private fun advanceRevision(conversationId: ConversationId) {
        revisions[conversationId] = (revisions[conversationId] ?: 0L) + 1L
    }

    override suspend fun removeMessageDraft(conversationId: ConversationId) {
        mutex.withLock {
            messageDraftDAO.removeMessageDraft(conversationId.toDao())
            advanceRevision(conversationId)
        }
    }
}
