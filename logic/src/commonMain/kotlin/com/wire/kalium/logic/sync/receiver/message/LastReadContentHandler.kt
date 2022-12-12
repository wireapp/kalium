package com.wire.kalium.logic.sync.receiver.message

import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.IsMessageSentInSelfConversationUseCase

interface LastReadContentHandler {
    suspend fun handle(
        message: Message.Signaling,
        messageContent: MessageContent.LastRead
    )
}

// This class handles the messages that arrive when some client has read the conversation.
internal class LastReadContentHandlerImpl internal constructor(
    private val conversationRepository: ConversationRepository,
    private val selfUserId: UserId,
    private val isMessageSentInSelfConversation: IsMessageSentInSelfConversationUseCase,
) : LastReadContentHandler {

    override suspend fun handle(
        message: Message.Signaling,
        messageContent: MessageContent.LastRead
    ) {
        val isMessageComingFromOtherClient = message.senderUserId == selfUserId
        val isMessageDestinedForSelfConversation: Boolean = isMessageSentInSelfConversation(message)

        if (isMessageComingFromOtherClient && isMessageDestinedForSelfConversation) {
            // If the message is coming from other client, it means that the user has read
            // the conversation on the other device, and we can update the read date locally
            // to synchronize the state across the clients.
            conversationRepository.updateConversationReadDate(
                qualifiedID = messageContent.conversationId,
                date = messageContent.time.toString()
            )
        }
    }

}
