package com.wire.kalium.logic.sync.receiver.message

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger

internal class DeleteForMeHandler internal constructor(
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val selfUserId: UserId,
    private val selfConversationIdProvider: SelfConversationIdProvider
) {

    suspend fun handle(
        message: Message,
        messageContent: MessageContent.DeleteForMe
    ) {
        // The conversationId comes with the hidden message[content] only carries the conversationId VALUE,
        // we need to get the DOMAIN from the self conversationId[here is the message.conversationId]
        val conversationId = messageContent.conversationId
            ?: ConversationId(messageContent.unqualifiedConversationId, selfUserId.domain)

        val isMessageReceivedFromSelfConversation: Boolean = selfConversationIdProvider().fold({ false }, { message.conversationId == it })
        if (isMessageReceivedFromSelfConversation) {
            messageRepository.deleteMessage(
                messageUuid = messageContent.messageId,
                conversationId = conversationId
            )
        } else {
            kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.EVENT_RECEIVER)
                .i(message = "Delete message sender is not verified: $message")
        }
    }

}
