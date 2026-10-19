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

package com.wire.kalium.logic.sync.receiver.handler

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.linkpreview.LinkPreviewMapperImpl
import com.wire.kalium.logic.data.message.mention.toDao
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent

/** Apply the app's edit rules, including rekeying an older echo while retaining a newer local edit. */
suspend fun editTextMessage(
    messageDAO: MessageDAO,
    currentMessage: Message,
    message: Message.Signaling,
    messageContent: MessageContent.TextEdited,
): Either<CoreFailure, Boolean> {
    if (currentMessage.senderUserId != message.senderUserId) return Either.Left(StorageFailure.DataNotFound)
    val editStatus = (currentMessage as? Message.Regular)?.editStatus
    val content = currentMessage.content
    val alreadyEdited = content is MessageContent.Text && editStatus is Message.EditStatus.Edited
    val keepLocalEdit = alreadyEdited && editStatus.lastEditInstant > message.date
    val update = if (keepLocalEdit) {
        messageContent.copy(newContent = content.value, newMentions = content.mentions)
    } else messageContent
    return wrapStorageRequest {
        messageDAO.updateTextMessageContent(
            editInstant = if (keepLocalEdit) editStatus.lastEditInstant else message.date,
            conversationId = message.conversationId.toDao(),
            currentMessageId = update.editMessageId,
            newTextContent = MessageEntityContent.Text(
                update.newContent,
                update.newLinkPreviews.map { LinkPreviewMapperImpl().fromModelToDao(it) },
                update.newMentions.map { it.toDao() }
            ),
            newMessageId = message.id,
            // A newer server echo completes a pending/failed local edit; a stale echo leaves its status alone.
            updateStatus = if (alreadyEdited && !keepLocalEdit) MessageEntity.Status.SENT else null,
        )
        !keepLocalEdit
    }
}

/** Returns whether the app should remove an existing alert. Expiration-driven deletion stays in the app. */
suspend fun MessageDAO.applyReceivedDeletion(
    messageId: String,
    conversationId: com.wire.kalium.persistence.dao.QualifiedIDEntity,
    deletion: ReceivedMessageDeletion,
): Either<CoreFailure, Boolean> = wrapStorageRequest {
    when (deletion) {
        ReceivedMessageDeletion.REMOVE -> {
            deleteMessage(messageId, conversationId)
            false
        }
        ReceivedMessageDeletion.TOMBSTONE -> {
            markMessageAsDeleted(messageId, conversationId)
            true
        }
        ReceivedMessageDeletion.NONE -> false
    }
}

/** A verified self-conversation signal removes the referenced message locally. */
suspend fun deleteMessageForMe(
    messageDAO: MessageDAO,
    content: MessageContent.DeleteForMe,
    isSelfConversation: Boolean,
): Either<CoreFailure, Boolean> = wrapStorageRequest {
    if (isSelfConversation) messageDAO.deleteMessage(content.messageId, content.conversationId.toDao())
    isSelfConversation
}

/** Compatibility with the heart reaction sent by older clients. */
fun MessageContent.Reaction.normalizedEmojiSet(): Set<String> =
    emojiSet.map { if (it == "❤") "❤️" else it }.toSet()
