package com.wire.kalium.logic.sync.receiver.conversation

import com.benasher44.uuid.uuid4
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ReceiptModeMapper
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.util.DateTimeUtil

interface ReceiptModeUpdateEventHandler {
    suspend fun handle(event: Event.Conversation.ConversationReceiptMode)
}

internal class ReceiptModeUpdateEventHandlerImpl(
    private val conversationDAO: ConversationDAO,
    private val persistMessage: PersistMessageUseCase,
    private val receiptModeMapper: ReceiptModeMapper = MapperProvider.receiptModeMapper()
) : ReceiptModeUpdateEventHandler {

    private val logger by lazy { kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.EVENT_RECEIVER) }

    override suspend fun handle(event: Event.Conversation.ConversationReceiptMode) {
        updateReceiptMode(event)
            .onSuccess {
                val message = Message.System(
                    uuid4().toString(),
                    MessageContent.ConversationReceiptModeChanged(
                        receiptMode = event.receiptMode == Conversation.ReceiptMode.ENABLED
                    ),
                    event.conversationId,
                    DateTimeUtil.currentIsoDateTimeString(),
                    event.senderUserId,
                    Message.Status.SENT,
                    Message.Visibility.VISIBLE
                )

                persistMessage(message)
                logger.d("[ReceiptModeUpdateEventHandler][Success] - Receipt Mode: [${event.receiptMode}]")
            }
            .onFailure { coreFailure ->
                logger.d("[ReceiptModeUpdateEventHandler][Error] - Receipt Mode: [${event.receiptMode}] " +
                        "| Conversation: [${event.conversationId.toString().obfuscateId()}] " +
                        "| CoreFailure: [$coreFailure]")
            }
    }

    private suspend fun updateReceiptMode(event: Event.Conversation.ConversationReceiptMode) = wrapStorageRequest {
        conversationDAO.updateConversationReceiptMode(
            event.conversationId.toDao(),
            receiptModeMapper.toDaoModel(event.receiptMode)
        )
    }

}
