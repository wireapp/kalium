package com.wire.kalium.logic.sync.receiver.message

import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.receipt.ReceiptRepository
import com.wire.kalium.logic.data.user.UserId
import kotlinx.datetime.Instant

interface ReceiptMessageHandler {
    suspend fun handle(
        message: Message.Signaling,
        messageContent: MessageContent.Receipt
    )
}

internal class ReceiptMessageHandlerImpl(
    private val selfUserId: UserId,
    private val receiptRepository: ReceiptRepository
): ReceiptMessageHandler {

    override suspend fun handle(
        message: Message.Signaling,
        messageContent: MessageContent.Receipt
    ) {
        // Receipts from self user shouldn't happen,
        // If it happens, it's unnecessary,
        // and we can squish some performance by skipping it completely
        if (message.senderUserId == selfUserId) return

        receiptRepository.persistReceipts(
            userId = message.senderUserId,
            conversationId = message.conversationId,
            date = Instant.parse(message.date),
            type = messageContent.type,
            messageIds = messageContent.messageIds
        )
    }
}
