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

    // TODO: for now we are just handeling the case when the self user has read the conversation
    // on another device, in the future we could handle the case when other user have read our message
    // here too
    suspend fun handle(
        message: Message,
        messageContent: MessageContent.LastRead
    ) {
        val isMessageComingFromOtherClient = message.senderUserId == userRepository.getSelfUserId()

        if (isMessageComingFromOtherClient) {
            // If the message is coming from other client, it means that the user has read
            // the conversation on the other device and we can update the read date locally
            // to synchronize the state across the clients.
            conversationRepository.updateConversationReadDate(
                qualifiedID = ConversationId(messageContent.conversationId, message.conversationId.domain),
                date = messageContent.time.toString()
            )
        }
    }

}
