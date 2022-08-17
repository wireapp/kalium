package com.wire.kalium.logic.sync.receiver.message

import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.util.TimeParser

// This class handles the messages that arrive when some client has read the conversation.
class LastReadContentHandler(
    private val conversationRepository: ConversationRepository,
    private val userRepository: UserRepository
) {

    suspend fun handle(
        message: Message,
        messageContent: MessageContent.LastRead
    ) {
        val isMessageComingFromOtherClient = message.senderUserId == userRepository.getSelfUserId()
        val isMessageDestinedForSelfConversation = conversationRepository.getSelfConversationId() == message.conversationId

        if (isMessageComingFromOtherClient && isMessageDestinedForSelfConversation) {
            // If the message is coming from other client, it means that the user has read
            // the conversation on the other device and we can update the read date locally
            // to synchronize the state across the clients.
            val conversationId = messageContent.conversationId ?: ConversationId(messageContent.unqualifiedConversationId, userRepository.getSelfUserId().domain)

            conversationRepository.updateConversationReadDate(
                qualifiedID = conversationId,
                date = messageContent.time.toString()
            )
        }
    }

}
