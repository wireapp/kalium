package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.receipt.DetailedReceipt
import com.wire.kalium.logic.data.message.receipt.ReceiptRepository
import com.wire.kalium.logic.data.message.receipt.ReceiptType
import kotlinx.coroutines.flow.Flow

/**
 * Use case to observe the receipts on a message
 *
 * @param receiptRepository ReceiptRepository for observing the selected message receipts
 *
 * @return Flow<List<DetailedReceipt>> - Flow of DetailedReceipt List that should be shown to the user.
 * That Flow emits everytime a receipt on the message is added.
 */
interface ObserveMessageReceiptsUseCase {
    suspend operator fun invoke(
        conversationId: ConversationId,
        messageId: String,
        type: ReceiptType
    ): Flow<List<DetailedReceipt>>
}

internal class ObserveMessageReceiptsUseCaseImpl(
    private val receiptRepository: ReceiptRepository
) : ObserveMessageReceiptsUseCase {

    override suspend fun invoke(
        conversationId: ConversationId,
        messageId: String,
        type: ReceiptType
    ): Flow<List<DetailedReceipt>> =
        receiptRepository.observeMessageReceipts(
            conversationId = conversationId,
            messageId = messageId,
            type
        )
}
