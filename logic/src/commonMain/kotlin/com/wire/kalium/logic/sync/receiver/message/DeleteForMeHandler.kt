package com.wire.kalium.logic.sync.receiver.message

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger

internal class DeleteForMeHandler internal constructor(
    private val messageRepository: MessageRepository,
    private val selfConversationIdProvider: SelfConversationIdProvider
) {

    suspend fun handle(
        messageContent: MessageContent.DeleteForMe,
        conversationId: ConversationId
    ) {
        val isMessageReceivedFromSelfConversation: Boolean = selfConversationIdProvider().fold({ false }, { conversationId == it })
        if (isMessageReceivedFromSelfConversation) {
            messageRepository.deleteMessage(
                messageUuid = messageContent.messageId,
                conversationId = messageContent.conversationId
            )
        } else {
            kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.EVENT_RECEIVER)
                .i(message = "Delete message sender is not verified: $messageContent")
        }
    }

}
