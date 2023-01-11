package com.wire.kalium.logic.sync.receiver.conversation

import com.benasher44.uuid.uuid4
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ReceiptModeMapper
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.IdMapper
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
    private val idMapper: IdMapper = MapperProvider.idMapper(),
    private val receiptModeMapper: ReceiptModeMapper = MapperProvider.receiptModeMapper()
) : ReceiptModeUpdateEventHandler {

    private val logger by lazy { kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.EVENT_RECEIVER) }

    override suspend fun handle(event: Event.Conversation.ConversationReceiptMode) {
        updateReceiptMode(event)
            .onSuccess {
                // todo: handle system message, we need to add it to db, see: conversation renamed
                logger.d("The Conversation receipt mode was updated: ${event.conversationId.toString().obfuscateId()}")
                val message = Message.System(
                    uuid4().toString(),
                    MessageContent.NewConversationReceiptMode( // ConversationReceiptModeChanged
                        receiptMode = event.receiptMode == Conversation.ReceiptMode.ENABLED
                    ),
                    event.conversationId,
                    DateTimeUtil.currentIsoDateTimeString(),
                    event.senderUserId,
                    Message.Status.SENT,
                    Message.Visibility.VISIBLE
                )

                persistMessage(message)
            }
            .onFailure { coreFailure ->
                logger.e("Error updating receipt mode for conversation [${event.conversationId.toString().obfuscateId()}] $coreFailure")
            }
    }

    private suspend fun updateReceiptMode(event: Event.Conversation.ConversationReceiptMode) = wrapStorageRequest {
        conversationDAO.updateConversationReceiptMode(
            event.conversationId.toDao(),
            receiptModeMapper.toDaoModel(event.receiptMode)
        )
    }

}
