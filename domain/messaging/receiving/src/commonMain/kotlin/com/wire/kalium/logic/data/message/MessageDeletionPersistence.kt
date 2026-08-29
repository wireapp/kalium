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

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.map
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.util.InternalKaliumApi

@InternalKaliumApi
public data class MessageDeletionSnapshot(
    val messageId: String,
    val conversationId: ConversationId,
    val senderUserId: UserId,
    val isRegularEphemeral: Boolean,
    val remoteAssetId: String?,
)

@InternalKaliumApi
public fun interface MessageDeletionPersistence {
    public suspend fun deleteMessage(
        messageUuid: String,
        conversationId: ConversationId,
    ): Either<StorageFailure, Unit>
}

@InternalKaliumApi
public interface IncomingMessageDeletionPersistence : MessageDeletionPersistence {
    public suspend fun loadMessageDeletionSnapshot(
        conversationId: ConversationId,
        messageId: String,
    ): Either<StorageFailure, MessageDeletionSnapshot>

    public suspend fun markMessageAsDeleted(
        messageUuid: String,
        conversationId: ConversationId,
    ): Either<StorageFailure, Unit>
}

@InternalKaliumApi
public class MessageDeletionPersistenceImpl public constructor(
    private val messageDAO: MessageDAO,
) : IncomingMessageDeletionPersistence {
    override suspend fun loadMessageDeletionSnapshot(
        conversationId: ConversationId,
        messageId: String,
    ): Either<StorageFailure, MessageDeletionSnapshot> = wrapStorageRequest {
        messageDAO.getMessageById(messageId, conversationId.toDao())
    }.map { message ->
        MessageDeletionSnapshot(
            messageId = message.id,
            conversationId = message.conversationId.toModel(),
            senderUserId = message.senderUserId.toModel(),
            isRegularEphemeral = message is MessageEntity.Regular && message.expireAfterMs != null,
            remoteAssetId = (message as? MessageEntity.Regular)
                ?.content
                ?.let { it as? MessageEntityContent.Asset }
                ?.assetId,
        )
    }

    override suspend fun deleteMessage(
        messageUuid: String,
        conversationId: ConversationId,
    ): Either<StorageFailure, Unit> = wrapStorageRequest {
        messageDAO.deleteMessage(messageUuid, conversationId.toDao())
    }

    override suspend fun markMessageAsDeleted(
        messageUuid: String,
        conversationId: ConversationId,
    ): Either<StorageFailure, Unit> = wrapStorageRequest {
        messageDAO.markMessageAsDeleted(messageUuid, conversationId.toDao())
    }
}
