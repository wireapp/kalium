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

import com.wire.kalium.persistence.MessageDraftsQueries
import com.wire.kalium.persistence.dao.ConversationIDEntity
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

class MessageDraftDAOImpl internal constructor(
    private val queries: MessageDraftsQueries,
    private val coroutineContext: CoroutineContext,
) : MessageDraftDAO {

    override suspend fun upsertMessageDraft(conversationIDEntity: ConversationIDEntity, messageDraft: MessageDraftEntity) =
        withContext(coroutineContext) {
            queries.upsertDraft(
                conversation_id = conversationIDEntity,
                text = messageDraft.text,
                edit_message_id = messageDraft.editMessageId,
                quoted_message_id = messageDraft.quotedMessageId,
                mention_list = messageDraft.selectedMentionList
            )
        }

    override suspend fun getMessageDraft(conversationIDEntity: ConversationIDEntity): MessageDraftEntity? =
        withContext(coroutineContext) {
            queries.getDraft(conversationIDEntity) { qualifiedIDEntity, text, editMessageID, quotedMessageId, mentionList ->
                MessageDraftEntity(
                    text = text.orEmpty(),
                    editMessageId = editMessageID,
                    quotedMessageId = quotedMessageId,
                    selectedMentionList = mentionList
                )
            }.executeAsOneOrNull()
        }

    override suspend fun removeMessageDraft(conversationIDEntity: ConversationIDEntity) {
        queries.deleteDraft(conversationIDEntity)
    }
}
