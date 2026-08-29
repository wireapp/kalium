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
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.message.receipt.ReceiptType
import com.wire.kalium.logic.data.message.receipt.toEntity
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.persistence.dao.reaction.ReactionDAO
import com.wire.kalium.persistence.dao.receipt.ReceiptDAO
import kotlinx.datetime.Instant

/** Local persistence operation caused by incoming last-read messages. */
public fun interface IncomingLastReadPersistence {
    public suspend fun updateReadDatesAndGetHasUnreadEvents(
        conversationDates: Map<ConversationId, Instant>,
    ): Either<StorageFailure, Map<ConversationId, Boolean>>
}

/** DAO-backed incoming last-read persistence shared by continuous and bounded event processing. */
public class IncomingLastReadPersistenceImpl public constructor(
    private val conversationDAO: ConversationDAO,
) : IncomingLastReadPersistence {
    override suspend fun updateReadDatesAndGetHasUnreadEvents(
        conversationDates: Map<ConversationId, Instant>,
    ): Either<StorageFailure, Map<ConversationId, Boolean>> =
        wrapStorageRequest {
            conversationDAO.updateReadDatesAndGetHasUnreadEvents(conversationDates.mapKeys { it.key.toDao() })
        }.map { hasUnreadByConversation ->
            hasUnreadByConversation.mapKeys { it.key.toModel() }
        }
}

/** Local persistence operations caused by an incoming receipt message. */
public interface IncomingReceiptPersistence {
    public suspend fun updateReferencedMessageStatusesIfNotRead(
        messageStatus: MessageEntity.Status,
        conversationId: ConversationId,
        messageIds: List<String>,
    ): Either<CoreFailure, Unit>

    public suspend fun insertReceipts(
        userId: UserId,
        conversationId: ConversationId,
        date: Instant,
        type: ReceiptType,
        messageIds: List<String>,
    )
}

/** DAO-backed incoming-receipt persistence shared by continuous and bounded event processing. */
public class IncomingReceiptPersistenceImpl public constructor(
    private val messageDAO: MessageDAO,
    private val receiptDAO: ReceiptDAO,
) : IncomingReceiptPersistence {
    override suspend fun updateReferencedMessageStatusesIfNotRead(
        messageStatus: MessageEntity.Status,
        conversationId: ConversationId,
        messageIds: List<String>,
    ): Either<CoreFailure, Unit> = wrapStorageRequest {
        messageDAO.updateMessagesStatusIfNotRead(messageStatus, conversationId.toDao(), messageIds)
    }

    override suspend fun insertReceipts(
        userId: UserId,
        conversationId: ConversationId,
        date: Instant,
        type: ReceiptType,
        messageIds: List<String>,
    ) {
        receiptDAO.insertReceipts(
            userId = userId.toDao(),
            conversationId = conversationId.toDao(),
            date = date,
            type = type.toEntity(),
            messageIds = messageIds,
        )
    }
}

/** Local persistence operation caused by an incoming reaction message. */
public fun interface IncomingReactionPersistence {
    public suspend fun updateReaction(
        originalMessageId: String,
        conversationId: ConversationId,
        senderUserId: UserId,
        instant: Instant,
        userReactions: UserReactions,
    ): Either<StorageFailure, Unit>
}

/** DAO-backed incoming-reaction persistence shared by continuous and bounded event processing. */
public class IncomingReactionPersistenceImpl public constructor(
    private val reactionDAO: ReactionDAO,
) : IncomingReactionPersistence {
    override suspend fun updateReaction(
        originalMessageId: String,
        conversationId: ConversationId,
        senderUserId: UserId,
        instant: Instant,
        userReactions: UserReactions,
    ): Either<StorageFailure, Unit> = wrapStorageRequest {
        reactionDAO.updateReactions(
            originalMessageId = originalMessageId,
            conversationId = conversationId.toDao(),
            senderUserId = senderUserId.toDao(),
            instant = instant,
            reactions = userReactions,
        )
    }
}

public data class MessageDeletionSnapshot(
    val messageId: String,
    val conversationId: ConversationId,
    val senderUserId: UserId,
    val isRegularEphemeral: Boolean,
    val remoteAssetId: String?,
)

public fun interface MessageDeletionPersistence {
    public suspend fun deleteMessage(
        messageUuid: String,
        conversationId: ConversationId,
    ): Either<StorageFailure, Unit>
}

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
