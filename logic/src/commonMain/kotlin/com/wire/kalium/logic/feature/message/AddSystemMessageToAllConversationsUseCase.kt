package com.wire.kalium.logic.feature.message

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.kaliumLogger

import com.wire.kalium.util.DateTimeUtil

/**
 * persist a local system message to all conversations
 */
interface AddSystemMessageToAllConversationsUseCase {
    suspend operator fun invoke()
}

class AddSystemMessageToAllConversationsUseCaseImpl internal constructor(
    private val messageRepository: MessageRepository,
    private val selfUserId: UserId
) : AddSystemMessageToAllConversationsUseCase {
    override suspend operator fun invoke() {
        kaliumLogger.w("persist HistoryLost system message after recovery for all conversations")
        val generatedMessageUuid = uuid4().toString()
        val message = Message.System(
            id = generatedMessageUuid,
            content = MessageContent.HistoryLost,
            // the conversation id will be ignored in the repo level!
            conversationId = ConversationId("", ""),
            date = DateTimeUtil.currentIsoDateTimeString(),
            senderUserId = selfUserId,
            status = Message.Status.SENT,
        )
        messageRepository.persistSystemMessageToAllConversations(message)
    }
}
