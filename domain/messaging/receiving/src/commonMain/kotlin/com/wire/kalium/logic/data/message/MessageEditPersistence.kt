/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.logic.data.message

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.map
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.message.attachment.toModel
import com.wire.kalium.logic.data.message.linkpreview.LinkPreviewMapper
import com.wire.kalium.logic.data.message.linkpreview.LinkPreviewMapperImpl
import com.wire.kalium.logic.data.message.mention.MessageMention
import com.wire.kalium.logic.data.message.mention.MessageMentionMapper
import com.wire.kalium.logic.data.message.mention.MessageMentionMapperImpl
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.util.InternalKaliumApi
import kotlinx.datetime.Instant

/** Minimal stored message state needed to arbitrate an incoming text or multipart edit. */
@InternalKaliumApi
public data class MessageEditState(
    val senderUserId: UserId,
    val content: Content,
) {
    public sealed interface Content {
        public data class Text(
            val value: String,
            val mentions: List<MessageMention>,
            val lastEditInstant: Instant?,
        ) : Content

        public data class Multipart(
            val value: String?,
            val mentions: List<MessageMention>,
            val attachments: List<MessageAttachment>,
            val lastEditInstant: Instant?,
        ) : Content

        public data object Other : Content
    }
}

/** Focused persistence used by incoming edit handlers and overlapping outgoing edit paths. */
@InternalKaliumApi
public interface MessageEditPersistence {
    public suspend fun loadMessageEditState(
        conversationId: ConversationId,
        messageId: String,
    ): Either<StorageFailure, MessageEditState>

    public suspend fun applyTextEdit(
        conversationId: ConversationId,
        messageContent: MessageContent.TextEdited,
        newMessageId: String,
        editInstant: Instant,
    ): Either<CoreFailure, Unit>

    public suspend fun applyMultipartEdit(
        conversationId: ConversationId,
        messageContent: MessageContent.MultipartEdited,
        newMessageId: String,
        editInstant: Instant,
    ): Either<CoreFailure, Unit>

    public suspend fun markMessageAsSent(
        conversationId: ConversationId,
        messageId: String,
    ): Either<CoreFailure, Unit>
}

/** DAO-backed edit persistence shared by continuous and bounded event processing and outgoing app paths. */
@InternalKaliumApi
public class MessageEditPersistenceImpl public constructor(
    private val messageDAO: MessageDAO,
    selfUserId: UserId,
    private val linkPreviewMapper: LinkPreviewMapper = LinkPreviewMapperImpl(),
    private val messageMentionMapper: MessageMentionMapper = MessageMentionMapperImpl(IdMapper(), selfUserId),
) : MessageEditPersistence {

    override suspend fun loadMessageEditState(
        conversationId: ConversationId,
        messageId: String,
    ): Either<StorageFailure, MessageEditState> = wrapStorageRequest {
        messageDAO.getMessageById(messageId, conversationId.toDao())
    }.map { message ->
        MessageEditState(
            senderUserId = message.senderUserId.toModel(),
            content = message.toEditContent(),
        )
    }

    override suspend fun applyTextEdit(
        conversationId: ConversationId,
        messageContent: MessageContent.TextEdited,
        newMessageId: String,
        editInstant: Instant,
    ): Either<CoreFailure, Unit> = wrapStorageRequest {
        messageDAO.updateTextMessageContent(
            editInstant = editInstant,
            conversationId = conversationId.toDao(),
            currentMessageId = messageContent.editMessageId,
            newTextContent = MessageEntityContent.Text(
                messageBody = messageContent.newContent,
                linkPreview = messageContent.newLinkPreviews.map(linkPreviewMapper::fromModelToDao),
                mentions = messageContent.newMentions.map(messageMentionMapper::fromModelToDao),
            ),
            newMessageId = newMessageId,
        )
    }

    override suspend fun applyMultipartEdit(
        conversationId: ConversationId,
        messageContent: MessageContent.MultipartEdited,
        newMessageId: String,
        editInstant: Instant,
    ): Either<CoreFailure, Unit> = wrapStorageRequest {
        messageDAO.updateTextMessageContent(
            editInstant = editInstant,
            conversationId = conversationId.toDao(),
            currentMessageId = messageContent.editMessageId,
            newTextContent = MessageEntityContent.Text(
                messageBody = messageContent.newTextContent ?: "",
                mentions = messageContent.newMentions.map(messageMentionMapper::fromModelToDao),
            ),
            newMessageId = newMessageId,
        )
    }

    override suspend fun markMessageAsSent(
        conversationId: ConversationId,
        messageId: String,
    ): Either<CoreFailure, Unit> = wrapStorageRequest {
        messageDAO.updateMessageStatus(
            status = MessageEntity.Status.SENT,
            id = messageId,
            conversationId = conversationId.toDao(),
        )
    }

    private fun MessageEntity.toEditContent(): MessageEditState.Content =
        if (this is MessageEntity.Regular) {
            val lastEditInstant = (editStatus as? MessageEntity.EditStatus.Edited)?.lastDate
            when (val currentContent = content) {
                is MessageEntityContent.Text -> MessageEditState.Content.Text(
                    value = currentContent.messageBody,
                    mentions = currentContent.mentions.map(messageMentionMapper::fromDaoToModel),
                    lastEditInstant = lastEditInstant,
                )

                is MessageEntityContent.Multipart -> MessageEditState.Content.Multipart(
                    value = currentContent.messageBody,
                    mentions = currentContent.mentions.map(messageMentionMapper::fromDaoToModel),
                    attachments = currentContent.attachments
                        .sortedBy { it.assetIndex }
                        .mapNotNull { it.toModel() },
                    lastEditInstant = lastEditInstant,
                )

                else -> MessageEditState.Content.Other
            }
        } else {
            MessageEditState.Content.Other
        }
}
