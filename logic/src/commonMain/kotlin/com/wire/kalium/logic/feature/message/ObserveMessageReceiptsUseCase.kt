package com.wire.kalium.logic.feature.message

import com.wire.kalium.logger.obfuscateDomain
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.receipt.DetailedReceipt
import com.wire.kalium.logic.data.message.receipt.ReceiptRepository
import com.wire.kalium.logic.data.message.receipt.ReceiptType
import com.wire.kalium.logic.kaliumLogger
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
        ).also {
            kaliumLogger.i("[ObserveMessageReceiptsUseCase] - Observing read receipts for " +
                    "Conversation: ${conversationId.value.obfuscateId()}@${conversationId.domain.obfuscateDomain()}")
        }
}
