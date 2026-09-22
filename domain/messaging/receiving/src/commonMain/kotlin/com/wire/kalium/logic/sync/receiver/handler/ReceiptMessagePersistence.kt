/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.sync.receiver.handler

import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.receipt.ReceiptType
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.receipt.ReceiptDAO
import com.wire.kalium.persistence.dao.receipt.ReceiptTypeEntity

/** Call inside the receiving SQL transaction; the app invokes its hook after this succeeds. */
suspend fun persistReceipt(
    messageDAO: MessageDAO,
    receiptDAO: ReceiptDAO,
    selfUserId: UserId,
    message: Message.Signaling,
    content: MessageContent.Receipt,
) = wrapStorageRequest {
    if (message.senderUserId != selfUserId) {
        messageDAO.updateMessagesStatusIfNotRead(content.type.toMessageStatus(), message.conversationId.toDao(), content.messageIds)
        receiptDAO.insertReceipts(
            message.senderUserId.toDao(),
            message.conversationId.toDao(),
            message.date,
            content.type.toReceiptEntity(),
            content.messageIds
        )
    }
}

fun ReceiptType.toMessageStatus(): MessageEntity.Status = when (this) {
    ReceiptType.READ -> MessageEntity.Status.READ
    ReceiptType.DELIVERED -> MessageEntity.Status.DELIVERED
}

fun ReceiptType.toReceiptEntity(): ReceiptTypeEntity = when (this) {
    ReceiptType.READ -> ReceiptTypeEntity.READ
    ReceiptType.DELIVERED -> ReceiptTypeEntity.DELIVERY
}
