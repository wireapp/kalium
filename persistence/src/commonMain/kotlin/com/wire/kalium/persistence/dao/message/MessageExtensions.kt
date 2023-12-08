/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.persistence.dao.message

import app.cash.paging.Pager
import app.cash.paging.PagingConfig
import app.cash.sqldelight.paging3.QueryPagingSource
import com.wire.kalium.persistence.MessagesQueries
import com.wire.kalium.persistence.dao.ConversationIDEntity
import kotlin.coroutines.CoroutineContext

interface MessageExtensions {
    fun getPagerForConversation(
        conversationId: ConversationIDEntity,
        visibilities: Collection<MessageEntity.Visibility>,
        pagingConfig: PagingConfig,
        startingOffset: Long
    ): KaliumPager<MessageEntity>

    fun getPagerForMessagesSearch(
        searchQuery: String,
        conversationId: ConversationIDEntity,
        pagingConfig: PagingConfig,
        startingOffset: Long
    ): KaliumPager<MessageEntity>
}

internal class MessageExtensionsImpl internal constructor(
    private val messagesQueries: MessagesQueries,
    private val messageMapper: MessageMapper,
    private val coroutineContext: CoroutineContext,
) : MessageExtensions {

    override fun getPagerForConversation(
        conversationId: ConversationIDEntity,
        visibilities: Collection<MessageEntity.Visibility>,
        pagingConfig: PagingConfig,
        startingOffset: Long
    ): KaliumPager<MessageEntity> {
        // We could return a Flow directly, but having the PagingSource is the only way to test this
        return KaliumPager(
            Pager(pagingConfig) { getPagingSource(conversationId, visibilities, startingOffset) },
            getPagingSource(conversationId, visibilities, startingOffset),
        )
    }
    override fun getPagerForMessagesSearch(
        searchQuery: String,
        conversationId: ConversationIDEntity,
        pagingConfig: PagingConfig,
        startingOffset: Long
    ): KaliumPager<MessageEntity> {
        // We could return a Flow directly, but having the PagingSource is the only way to test this
        return KaliumPager(
            Pager(pagingConfig) { getMessagesSearchPagingSource(searchQuery, conversationId, startingOffset) },
            getMessagesSearchPagingSource(searchQuery, conversationId, startingOffset),
        )
    }

    private fun getPagingSource(
        conversationId: ConversationIDEntity,
        visibilities: Collection<MessageEntity.Visibility>,
        initialOffset: Long
    ) = QueryPagingSource(
            countQuery = messagesQueries.countByConversationIdAndVisibility(conversationId, visibilities),
            transacter = messagesQueries,
            context = coroutineContext,
            initialOffset = initialOffset,
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

    private fun getMessagesSearchPagingSource(
        searchQuery: String,
        conversationId: ConversationIDEntity,
        initialOffset: Long
    ) = QueryPagingSource(
            countQuery = messagesQueries.countBySearchedMessageAndConversationId(searchQuery, conversationId),
            transacter = messagesQueries,
            context = coroutineContext,
            initialOffset = initialOffset,
            queryProvider = { limit, offset ->
                messagesQueries.selectConversationMessagesFromSearch(
                    searchQuery,
                    conversationId,
                    limit,
                    offset,
                    messageMapper::toEntityMessageFromView
                )
            }
        )
}
