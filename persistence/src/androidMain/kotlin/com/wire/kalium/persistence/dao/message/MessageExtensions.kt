package com.wire.kalium.persistence.dao.message

import androidx.paging.Pager
import androidx.paging.PagingConfig
import app.cash.sqldelight.paging3.QueryPagingSource
import com.wire.kalium.persistence.MessagesQueries
import com.wire.kalium.persistence.dao.ConversationIDEntity
import kotlin.coroutines.CoroutineContext

actual interface MessageExtensions {
    fun getPagerForConversation(
        conversationId: ConversationIDEntity,
        visibilities: Collection<MessageEntity.Visibility>,
        pagingConfig: PagingConfig,
    ): KaliumPager<MessageEntity>
}

actual class MessageExtensionsImpl actual constructor(
    private val messagesQueries: MessagesQueries,
    private val messageMapper: MessageMapper,
    private val coroutineContext: CoroutineContext
) : MessageExtensions {

    override fun getPagerForConversation(
        conversationId: ConversationIDEntity,
        visibilities: Collection<MessageEntity.Visibility>,
        pagingConfig: PagingConfig
    ): KaliumPager<MessageEntity> {
        // We could return a Flow directly, but having the PagingSource is the only way to test this
        return KaliumPager(
            Pager(pagingConfig) { getPagingSource(conversationId, visibilities) },
            getPagingSource(conversationId, visibilities),
            coroutineContext
        )
    }

    private fun getPagingSource(
        conversationId: ConversationIDEntity,
        visibilities: Collection<MessageEntity.Visibility>
    ) =
        QueryPagingSource(
            countQuery = messagesQueries.countByConversationIdAndVisibility(conversationId, visibilities),
            transacter = messagesQueries,
            queryProvider = { limit, offset ->
                messagesQueries.selectByConversationIdAndVisibility(
                    conversationId,
                    visibilities,
                    limit,
                    offset,
                    messageMapper::toEntityMessageFromView
                )
            }
        )
}
