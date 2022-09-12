package com.wire.kalium.persistence.dao.message

import androidx.paging.Pager
import androidx.paging.PagingConfig
import com.wire.kalium.persistence.Message
import com.wire.kalium.persistence.MessagesQueries
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.paging.QueryPagingSource

actual interface MessageExtensions {
    fun getPagerForConversation(
        conversationId: ConversationIDEntity,
        visibilities: Collection<MessageEntity.Visibility>,
        pagingConfig: PagingConfig
    ): KaliumPager<Message, MessageEntity>
}

actual class MessageExtensionsImpl actual constructor(
    private val messagesQueries: MessagesQueries,
    private val messageMapper: MessageMapper
) : MessageExtensions {

    override fun getPagerForConversation(
        conversationId: ConversationIDEntity,
        visibilities: Collection<MessageEntity.Visibility>,
        pagingConfig: PagingConfig
    ): KaliumPager<Message, MessageEntity> {
        val pagingSource = QueryPagingSource(
            countQuery = messagesQueries.countByConversationIdAndVisibility(conversationId, visibilities),
            transacter = messagesQueries,
            queryProvider = { limit, offset ->
                messagesQueries.selectByConversationIdAndVisibility(conversationId, visibilities, limit, offset)
            }
        )
        // We could return a Flow directly, but having the PagingSource is the only way to test this
        return KaliumPager(Pager(pagingConfig) { pagingSource }, pagingSource) { message ->
            messageMapper.toMessageEntity(message)
        }
    }
}
