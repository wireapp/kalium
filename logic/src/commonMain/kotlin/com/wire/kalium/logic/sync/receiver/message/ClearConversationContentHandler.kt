package com.wire.kalium.logic.sync.receiver.message

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.IsMessageSentInSelfConversationUseCase
import com.wire.kalium.logic.feature.conversation.ClearConversationContent

interface ClearConversationContentHandler {
    suspend fun handle(
        message: Message.Signaling,
        messageContent: MessageContent.Cleared
    )
}

internal class ClearConversationContentHandlerImpl(
    private val clearConversationContent: ClearConversationContent,
    private val selfUserId: UserId,
    private val isMessageSentInSelfConversation: IsMessageSentInSelfConversationUseCase
) : ClearConversationContentHandler {

    override suspend fun handle(
        message: Message.Signaling,
        messageContent: MessageContent.Cleared
    ) {
        val isMessageComingFromOtherClient = message.senderUserId == selfUserId
        val isMessageDestinedForSelfConversation: Boolean = isMessageSentInSelfConversation(message)

        if (isMessageComingFromOtherClient && isMessageDestinedForSelfConversation) {
            val conversationId = messageContent.conversationId
                ?: ConversationId(
                    value = messageContent.unqualifiedConversationId,
                    domain = selfUserId.domain
                )

            clearConversationContent(conversationId)
        }
    }
}
