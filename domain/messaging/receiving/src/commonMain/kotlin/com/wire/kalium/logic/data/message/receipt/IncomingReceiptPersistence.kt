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

package com.wire.kalium.logic.data.message.receipt

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.receipt.ReceiptDAO
import com.wire.kalium.util.InternalKaliumApi
import kotlinx.datetime.Instant

/** Local persistence operations caused by an incoming receipt message. */
@InternalKaliumApi
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
@InternalKaliumApi
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
