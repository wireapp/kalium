package com.wire.kalium.logic.feature.message

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.data.user.UserId

import com.wire.kalium.util.DateTimeUtil
import kotlinx.coroutines.flow.first

/**
 * persist a local system message to all conversations
 */
class AddSystemMessageToAllConversationsUseCase internal constructor(
    private val messageRepository: MessageRepository,
    private val slowSyncRepository: SlowSyncRepository,
    private val selfUserId: UserId
) {
    suspend operator fun invoke() {
        slowSyncRepository.slowSyncStatus.first {
            it is SlowSyncStatus.Complete
        }
        val generatedMessageUuid = uuid4().toString()
        val message = Message.System(
            id = generatedMessageUuid,
            content = MessageContent.HistoryLost,
            //the conversation id will be ignored in the repo level!
            conversationId = ConversationId("", ""),
            date = DateTimeUtil.currentIsoDateTimeString(),
            senderUserId = selfUserId,
            status = Message.Status.SENT,
        )
        messageRepository.persistSystemMessageToAllConversations(message)
    }
}
