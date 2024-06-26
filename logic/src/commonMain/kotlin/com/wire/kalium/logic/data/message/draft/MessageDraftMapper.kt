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

import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.message.mention.toDao
import com.wire.kalium.logic.data.message.mention.toModel
import com.wire.kalium.persistence.dao.message.draft.MessageDraftEntity

fun MessageDraftEntity.toModel(): MessageDraft = MessageDraft(
    conversationId = conversationId.toModel(),
    text = text,
    editMessageId = editMessageId,
    quotedMessageId = quotedMessageId,
    // self mentions are not supported in drafts
    selectedMentionList = selectedMentionList.map {
        it.toModel(selfUserId = null)
    }
)

fun MessageDraft.toDao(): MessageDraftEntity = MessageDraftEntity(
    conversationId = conversationId.toDao(),
    text = text,
    editMessageId = editMessageId,
    quotedMessageId = quotedMessageId,
    selectedMentionList = selectedMentionList.map {
        it.toDao()
    }
)
