package com.wire.kalium.logic.data.message

import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.persistence.dao.message.MessageEntity
import kotlinx.coroutines.flow.map

suspend fun MessageRepository.getPaginatedMessagesByConversationIdAndVisibility(
    conversationId: ConversationId,
    visibility: List<Message.Visibility>,
    pagingConfig: PagingConfig,
) = run {
    // TODO: Fix this ugly shit
    this as MessageDataSource
    messageDAO.platformExtensions.getPaginatedConversationFlow(
        idMapper.toDaoModel(conversationId),
        visibility.map { it.toEntityVisibility() },
        pagingConfig
    ).map { pagingData: PagingData<MessageEntity> ->
        pagingData.map(messageMapper::fromEntityToMessage)
    }
}
