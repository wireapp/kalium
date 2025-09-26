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
package com.wire.kalium.persistence.dao.message.draft

import app.cash.sqldelight.coroutines.asFlow
import com.wire.kalium.persistence.ConversationsQueries
import com.wire.kalium.persistence.MessageDraftsQueries
import com.wire.kalium.persistence.MessagesQueries
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.message.draft.MessageDraftMapper.toDao
import com.wire.kalium.persistence.db.ReadDispatcher
import com.wire.kalium.persistence.db.WriteDispatcher
import com.wire.kalium.persistence.util.mapToList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class MessageDraftDAOImpl internal constructor(
    private val queries: MessageDraftsQueries,
    private val messagesQueries: MessagesQueries,
    private val conversationsQueries: ConversationsQueries,
    private val readDispatcher: ReadDispatcher,
    private val writeDispatcher: WriteDispatcher,
) : MessageDraftDAO {

    override suspend fun upsertMessageDraft(messageDraft: MessageDraftEntity) =
        withContext(writeDispatcher.value) {
            val conversationExists = conversationsQueries.selectConversationByQualifiedId(messageDraft.conversationId)
                .executeAsOneOrNull() != null

            if (!conversationExists) {
                return@withContext
            }

            if (messageDraft.editMessageId != null) {
                val messageExists = messagesQueries.getMessage(messageDraft.editMessageId, messageDraft.conversationId)
                    .executeAsOneOrNull() != null
                if (!messageExists) {
                    return@withContext
                }
            }

            if (messageDraft.quotedMessageId != null) {
                val quotedMessageExists = messagesQueries.getMessage(messageDraft.quotedMessageId, messageDraft.conversationId)
                    .executeAsOneOrNull() != null
                if (!quotedMessageExists) {
                    return@withContext
                }
            }

            queries.transaction {
                queries.upsertDraft(
                    conversation_id = messageDraft.conversationId,
                    text = messageDraft.text,
                    edit_message_id = messageDraft.editMessageId,
                    quoted_message_id = messageDraft.quotedMessageId,
                    mention_list = messageDraft.selectedMentionList
                )
                val changes = queries.selectChanges().executeAsOne()
                if (changes == 0L) {
                    // rollback the transaction if no changes were made so that it doesn't notify other queries about changes if not needed
                    this.rollback()
                }
            }
        }

    override suspend fun getMessageDraft(conversationIDEntity: ConversationIDEntity): MessageDraftEntity? =
        withContext(readDispatcher.value) {
            queries.getDraft(conversationIDEntity, ::toDao).executeAsOneOrNull()
        }

    override suspend fun removeMessageDraft(conversationIDEntity: ConversationIDEntity) = withContext(writeDispatcher.value) {
        queries.deleteDraft(conversationIDEntity)
    }

    override suspend fun observeMessageDrafts(): Flow<List<MessageDraftEntity>> = queries.getDrafts(::toDao)
        .asFlow()
        .flowOn(readDispatcher.value)
        .mapToList()
}
