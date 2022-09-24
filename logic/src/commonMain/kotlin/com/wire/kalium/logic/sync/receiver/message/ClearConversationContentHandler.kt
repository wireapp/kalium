package com.wire.kalium.logic.sync.receiver.message

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.SelfConversationIdProvider
import com.wire.kalium.logic.feature.conversation.ClearConversationContent
import com.wire.kalium.logic.functional.fold

internal class ClearConversationContentHandler(
    private val clearConversationContent: ClearConversationContent,
    private val selfUserId: UserId,
    private val selfConversationIdProvider: SelfConversationIdProvider
) {

    suspend fun handle(
        message: Message,
        messageContent: MessageContent.Cleared
    ) {
        val isMessageComingFromOtherClient = message.senderUserId == selfUserId
        val isMessageDestinedForSelfConversation: Boolean = selfConversationIdProvider().fold({ false }, { it == message.conversationId })

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
