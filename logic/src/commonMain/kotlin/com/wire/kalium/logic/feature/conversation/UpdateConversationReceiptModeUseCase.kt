package com.wire.kalium.logic.feature.conversation

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.util.DateTimeUtil

interface UpdateConversationReceiptModeUseCase {
    suspend operator fun invoke(
        conversationId: ConversationId,
        receiptMode: Conversation.ReceiptMode
    ): ConversationUpdateReceiptModeResult
}

internal class UpdateConversationReceiptModeUseCaseImpl(
    private val conversationRepository: ConversationRepository,
    private val persistMessage: PersistMessageUseCase,
    private val selfUserId: UserId
) : UpdateConversationReceiptModeUseCase {

    override suspend fun invoke(
        conversationId: ConversationId,
        receiptMode: Conversation.ReceiptMode
    ): ConversationUpdateReceiptModeResult =
        conversationRepository.updateReceiptMode(
            conversationId = conversationId,
            receiptMode = receiptMode
        ).fold({
            ConversationUpdateReceiptModeResult.Failure(it)
        }, {
            handleSystemMessage(
                conversationId = conversationId,
                receiptMode = receiptMode
            )
            ConversationUpdateReceiptModeResult.Success
        })

    private suspend fun handleSystemMessage(
        conversationId: ConversationId,
        receiptMode: Conversation.ReceiptMode
    ) {
        val message = Message.System(
            uuid4().toString(),
            MessageContent.ConversationReceiptModeChanged(
                receiptMode = receiptMode == Conversation.ReceiptMode.ENABLED
            ),
            conversationId,
            DateTimeUtil.currentIsoDateTimeString(),
            selfUserId,
            Message.Status.SENT,
            Message.Visibility.VISIBLE
        )

        persistMessage(message)
    }
}

sealed interface ConversationUpdateReceiptModeResult {
    object Success : ConversationUpdateReceiptModeResult
    data class Failure(val cause: CoreFailure) : ConversationUpdateReceiptModeResult
}
