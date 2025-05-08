/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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
import com.wire.kalium.persistence.MessageAssetViewQueries
import com.wire.kalium.persistence.MessagesQueries
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.asset.AssetMessageEntity
import com.wire.kalium.persistence.kaliumLogger
import io.mockative.Mockable
import kotlin.coroutines.CoroutineContext

@Mockable
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

    fun getPagerForMessageAssetsWithoutImage(
        conversationId: ConversationIDEntity,
        mimeTypes: Set<String>,
        pagingConfig: PagingConfig,
        startingOffset: Long
    ): KaliumPager<MessageEntity>

    fun getPagerForMessageAssetImage(
        conversationId: ConversationIDEntity,
        mimeTypes: Set<String>,
        pagingConfig: PagingConfig,
        startingOffset: Long
    ): KaliumPager<AssetMessageEntity>
}

internal class MessageExtensionsImpl internal constructor(
    private val messagesQueries: MessagesQueries,
    private val messageAssetViewQueries: MessageAssetViewQueries,
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
            coroutineContext
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
            coroutineContext
        )
    }

    override fun getPagerForMessageAssetsWithoutImage(
        conversationId: ConversationIDEntity,
        mimeTypes: Set<String>,
        pagingConfig: PagingConfig,
        startingOffset: Long
    ): KaliumPager<MessageEntity> {
        // We could return a Flow directly, but having the PagingSource is the only way to test this
        return KaliumPager(
            Pager(pagingConfig) { getMessageAssetsWithoutImagePagingSource(conversationId, mimeTypes, startingOffset) },
            getMessageAssetsWithoutImagePagingSource(conversationId, mimeTypes, startingOffset),
            coroutineContext
        )
    }

    override fun getPagerForMessageAssetImage(
        conversationId: ConversationIDEntity,
        mimeTypes: Set<String>,
        pagingConfig: PagingConfig,
        startingOffset: Long
    ): KaliumPager<AssetMessageEntity> {
        return KaliumPager(
            Pager(pagingConfig) { getMessageImageAssetsPagingSource(conversationId, mimeTypes, startingOffset) },
            getMessageImageAssetsPagingSource(conversationId, mimeTypes, startingOffset),
            coroutineContext
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
                kaliumLogger.d("[QueryPagingSource] Loading [MessageEntity] data: offset = $offset limit = $limit")
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

    private fun getMessageAssetsWithoutImagePagingSource(
        conversationId: ConversationIDEntity,
        mimeTypes: Set<String>,
        initialOffset: Long
    ) = QueryPagingSource(
        countQuery = messageAssetViewQueries.countAssetMessagesByConversationIdAndMimeTypes(
            conversationId,
            listOf(MessageEntity.Visibility.VISIBLE),
            listOf(MessageEntity.ContentType.ASSET),
            mimeTypes
        ),
        transacter = messageAssetViewQueries,
        context = coroutineContext,
        initialOffset = initialOffset,
        queryProvider = { limit, offset ->
            messageAssetViewQueries.getAssetMessagesByConversationIdAndMimeTypes(
                conversationId,
                listOf(MessageEntity.Visibility.VISIBLE),
                listOf(MessageEntity.ContentType.ASSET),
                mimeTypes,
                limit,
                offset,
                messageMapper::toEntityMessageFromView
            )
        }
    )

    private fun getMessageImageAssetsPagingSource(
        conversationId: ConversationIDEntity,
        mimeTypes: Set<String>,
        initialOffset: Long
    ) = QueryPagingSource(
        countQuery = messageAssetViewQueries.countImageAssetMessagesByConversationIdAndMimeTypes(
            conversationId,
            listOf(MessageEntity.Visibility.VISIBLE),
            listOf(MessageEntity.ContentType.ASSET),
            mimeTypes
        ),
        transacter = messageAssetViewQueries,
        context = coroutineContext,
        initialOffset = initialOffset,
        queryProvider = { limit, offset ->
            messageAssetViewQueries.getImageAssetMessagesByConversationIdAndMimeTypes(
                conversationId,
                listOf(MessageEntity.Visibility.VISIBLE),
                listOf(MessageEntity.ContentType.ASSET),
                mimeTypes,
                limit,
                offset,
                messageMapper::toEntityAssetMessageFromView
            )
        }
    )
}
