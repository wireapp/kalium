package com.wire.kalium.logic.sync.receiver.conversation

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.kaliumLogger
import kotlinx.datetime.Clock

interface NewConversationEventHandler {
    suspend fun handle(event: Event.Conversation.NewConversation): Either<CoreFailure, Unit>
}

internal class NewConversationEventHandlerImpl(
    private val conversationRepository: ConversationRepository
) : NewConversationEventHandler {
    private val logger by lazy { kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.EVENT_RECEIVER) }

    override suspend fun handle(event: Event.Conversation.NewConversation) =
        conversationRepository.insertConversationFromEvent(event).flatMap {
            conversationRepository.updateConversationModifiedDate(event.conversationId, Clock.System.now().toString())
        }.onFailure { logger.e("failure on new conversation event: $it") }

}
