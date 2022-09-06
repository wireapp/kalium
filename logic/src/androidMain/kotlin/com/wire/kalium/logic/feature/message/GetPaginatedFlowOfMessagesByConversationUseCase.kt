package com.wire.kalium.logic.feature.message

import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.getPaginatedMessagesByConversationIdAndVisibility
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class GetPaginatedFlowOfMessagesByConversationUseCase internal constructor(
    private val messageRepository: MessageRepository,
    private val slowSyncRepository: SlowSyncRepository
) {

    suspend operator fun invoke(
        conversationId: ConversationId,
        visibility: List<Message.Visibility> = Message.Visibility.values().toList(),
        pagingConfig: PagingConfig
    ): Flow<PagingData<Message>> {
        slowSyncRepository.slowSyncStatus.first {
            it is SlowSyncStatus.Complete
        }
        return messageRepository.getPaginatedMessagesByConversationIdAndVisibility(
            conversationId, visibility, pagingConfig
        )
    }
}
