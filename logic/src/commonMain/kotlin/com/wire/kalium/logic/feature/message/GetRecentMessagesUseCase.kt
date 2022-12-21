package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Gets the recent messages from the conversation
 */
class GetRecentMessagesUseCase internal constructor(
    private val messageRepository: MessageRepository,
    private val slowSyncRepository: SlowSyncRepository
) {

    /**
     * @param conversationId the id of the conversation
     * @param limit the number of messages to return for pagination
     * @param offset the offset of the messages to return for pagination
     * @param visibility the visibility of the messages to return @see [Message.Visibility]
     * @return the [Flow] of [List] of [Message] if successful
     */
    suspend operator fun invoke(
        conversationId: ConversationId,
        limit: Int = 100,
        offset: Int = 0,
        visibility: List<Message.Visibility> = Message.Visibility.values().toList()
    ): Flow<List<Message>> {
        slowSyncRepository.slowSyncStatus.first {
            it is SlowSyncStatus.Complete
        }
        return messageRepository.getMessagesByConversationIdAndVisibility(conversationId, limit, offset, visibility)
    }
}
