package com.wire.kalium.logic.data.message

import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.persistence.dao.message.KaliumPager
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

actual interface MessageRepositoryExtensions {
    suspend fun getPaginatedMessagesByConversationIdAndVisibility(
        conversationId: ConversationId,
        visibility: List<Message.Visibility>,
        pagingConfig: PagingConfig,
    ): Flow<PagingData<Message>>
}

actual class MessageRepositoryExtensionsImpl actual constructor(
    private val messageDAO: MessageDAO,
    private val idMapper: IdMapper,
    private val messageMapper: MessageMapper,
) : MessageRepositoryExtensions {

    override suspend fun getPaginatedMessagesByConversationIdAndVisibility(
        conversationId: ConversationId,
        visibility: List<Message.Visibility>,
        pagingConfig: PagingConfig,
    ): Flow<PagingData<Message>> {
        val pager: KaliumPager<MessageEntity> = messageDAO.platformExtensions.getPagerForConversation(
            idMapper.toDaoModel(conversationId),
            visibility.map { it.toEntityVisibility() },
            pagingConfig
        )

        return pager.pagingDataFlow.map { pagingData: PagingData<MessageEntity> ->
            pagingData.map(messageMapper::fromEntityToMessage)
        }
    }
}
