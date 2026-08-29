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

package com.wire.kalium.logic.data.message

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.MessageId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.persistence.dao.message.ButtonEntity
import com.wire.kalium.persistence.dao.message.CompositeMessageDAO
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import kotlinx.datetime.Instant

public interface CompositeMessageRepository {
    public suspend fun markSelected(
        messageId: MessageId,
        conversationId: ConversationId,
        buttonId: String,
    ): Either<StorageFailure, Unit>

    public suspend fun resetSelection(
        messageId: MessageId,
        conversationId: ConversationId,
    ): Either<StorageFailure, Unit>

    public suspend fun updateCompositeMessage(
        conversationId: ConversationId,
        messageContent: MessageContent.CompositeEdited,
        newMessageId: MessageId,
        editInstant: Instant,
    ): Either<StorageFailure, Unit>
}

public class CompositeMessageDataSource public constructor(
    private val compositeMessageDAO: CompositeMessageDAO,
    private val messageDAO: MessageDAO,
) : CompositeMessageRepository {
    override suspend fun markSelected(
        messageId: MessageId,
        conversationId: ConversationId,
        buttonId: String,
    ): Either<StorageFailure, Unit> = wrapStorageRequest {
        compositeMessageDAO.markAsSelected(
            messageId = messageId,
            conversationId = conversationId.toDao(),
            buttonId = buttonId,
        )
    }

    override suspend fun resetSelection(
        messageId: MessageId,
        conversationId: ConversationId,
    ): Either<StorageFailure, Unit> = wrapStorageRequest {
        compositeMessageDAO.resetSelection(
            messageId = messageId,
            conversationId = conversationId.toDao(),
        )
    }

    override suspend fun updateCompositeMessage(
        conversationId: ConversationId,
        messageContent: MessageContent.CompositeEdited,
        newMessageId: MessageId,
        editInstant: Instant,
    ): Either<StorageFailure, Unit> = wrapStorageRequest {
        messageDAO.updateCompositeMessageContent(
            conversationId = conversationId.toDao(),
            currentMessageId = messageContent.editMessageId,
            editInstant = editInstant,
            newCompositeContent = MessageEntityContent.Composite(
                text = messageContent.newTextContent?.value?.let {
                    MessageEntityContent.Text(messageBody = it)
                },
                buttonList = messageContent.newButtonList.map {
                    ButtonEntity(it.text, it.id, it.isSelected)
                },
            ),
            newMessageId = newMessageId,
        )
    }
}
