package com.wire.kalium.logic.sync.receiver.conversation

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger

interface RenamedConversationEventHandler {
    suspend fun handle(event: Event.Conversation.RenamedConversation)
}

internal class RenamedConversationEventHandlerImpl(
    private val conversationRepository: ConversationRepository,
    private val persistMessage: PersistMessageUseCase
) : RenamedConversationEventHandler {
    private val logger by lazy { kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.EVENT_RECEIVER) }

    override suspend fun handle(event: Event.Conversation.RenamedConversation) {
        conversationRepository.updateConversationName(event.conversationId, event.conversationName, event.timestampIso)
            .onSuccess {
                logger
                    .d("The Conversation was renamed: ${event.conversationId.toString().obfuscateId()}")
                val message = Message.System(
                    id = event.id,
                    content = MessageContent.ConversationRenamed(event.conversationName),
                    conversationId = event.conversationId,
                    date = event.timestampIso,
                    senderUserId = event.senderUserId,
                    status = Message.Status.SENT,
                )
                persistMessage(message)
            }
            .onFailure { coreFailure ->
                logger
                    .e("Error renaming the conversation [${event.conversationId.toString().obfuscateId()}] $coreFailure")
            }
    }
}
