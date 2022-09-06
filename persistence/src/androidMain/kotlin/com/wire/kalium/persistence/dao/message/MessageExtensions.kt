package com.wire.kalium.persistence.dao.message

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.wire.kalium.persistence.MessagesQueries
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.paging.QueryPagingSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

actual class MessageExtensions actual constructor(
    private val messagesQueries: MessagesQueries,
    private val messageMapper: MessageMapper
) {
    fun getPaginatedConversationFlow(
        conversationId: ConversationIDEntity,
        visibilities: Collection<MessageEntity.Visibility>,
        pagingConfig: PagingConfig
    ): Flow<PagingData<MessageEntity>> {
        return Pager(pagingConfig) {
            QueryPagingSource(
                countQuery = messagesQueries.countByConversationIdAndVisibility(conversationId, visibilities),
                transacter = messagesQueries,
                queryProvider = { limit, offset ->
                    messagesQueries.selectByConversationIdAndVisibility(conversationId, visibilities, limit, offset)
                }
            )
        }.flow.map {
            it.map { message -> messageMapper.toMessageEntity(message) }
        }
    }
}
