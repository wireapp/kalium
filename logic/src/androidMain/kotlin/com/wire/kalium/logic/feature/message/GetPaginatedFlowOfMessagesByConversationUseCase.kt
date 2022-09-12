package com.wire.kalium.logic.feature.message

import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.util.KaliumDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn

class GetPaginatedFlowOfMessagesByConversationUseCase internal constructor(
    private val dispatcher: KaliumDispatcher,
    private val messageRepository: MessageRepository,
) {

    suspend operator fun invoke(
        conversationId: ConversationId,
        visibility: List<Message.Visibility> = Message.Visibility.values().toList(),
        pagingConfig: PagingConfig
    ): Flow<PagingData<Message>> = messageRepository.extensions.getPaginatedMessagesByConversationIdAndVisibility(
        conversationId, visibility, pagingConfig
    ).flowOn(dispatcher.io)
}
