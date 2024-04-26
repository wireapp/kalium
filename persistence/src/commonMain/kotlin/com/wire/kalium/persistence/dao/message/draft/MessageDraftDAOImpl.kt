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
import com.wire.kalium.persistence.MessageDraftsQueries
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.util.mapToList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

class MessageDraftDAOImpl internal constructor(
    private val queries: MessageDraftsQueries,
    private val coroutineContext: CoroutineContext,
) : MessageDraftDAO {

    override suspend fun upsertMessageDraft(messageDraft: MessageDraftEntity) =
        withContext(coroutineContext) {
            queries.upsertDraft(
                conversation_id = messageDraft.conversationId,
                text = messageDraft.text,
                edit_message_id = messageDraft.editMessageId,
                quoted_message_id = messageDraft.quotedMessageId,
                mention_list = messageDraft.selectedMentionList
            )
        }

    override suspend fun getMessageDraft(conversationIDEntity: ConversationIDEntity): MessageDraftEntity? =
        withContext(coroutineContext) {
            queries.getDraft(conversationIDEntity, ::toDao).executeAsOneOrNull()
        }

    override suspend fun removeMessageDraft(conversationIDEntity: ConversationIDEntity) {
        queries.deleteDraft(conversationIDEntity)
    }

    override suspend fun observeMessageDrafts(): Flow<List<MessageDraftEntity>> = queries.getDrafts(::toDao)
        .asFlow()
        .flowOn(coroutineContext)
        .mapToList()

    private fun toDao(
        conversationId: QualifiedIDEntity,
        text: String?,
        editMessageId: String?,
        quotedMessageId: String?,
        mentionList: List<MessageEntity.Mention>
    ): MessageDraftEntity =
        MessageDraftEntity(
            conversationId = conversationId,
            text = text.orEmpty(),
            editMessageId = editMessageId,
            quotedMessageId = quotedMessageId,
            selectedMentionList = mentionList
        )
}
