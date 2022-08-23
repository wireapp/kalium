package com.wire.kalium.logic.sync.receiver.message

import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.conversation.ClearConversationContent

internal class ClearConversationContentHandler(
    private val conversationRepository: ConversationRepository,
    private val userRepository: UserRepository,
    private val clearConversationContent: ClearConversationContent
) {

    suspend fun handle(
        message: Message,
        messageContent: MessageContent.Cleared
    ) {
        val isMessageComingFromOtherClient = message.senderUserId == userRepository.getSelfUserId()
        val isMessageDestinedForSelfConversation = conversationRepository.getSelfConversationId() == message.conversationId

        if (isMessageComingFromOtherClient && isMessageDestinedForSelfConversation) {
            val conversationId = messageContent.conversationId
                ?: ConversationId(messageContent.unqualifiedConversationId, userRepository.getSelfUserId().domain)

            clearConversationContent(conversationId)
        }
    }
}
