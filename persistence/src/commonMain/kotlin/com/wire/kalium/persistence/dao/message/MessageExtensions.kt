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
import com.wire.kalium.persistence.MessageDetailsView
import com.wire.kalium.persistence.MessagesQueries
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.asset.AssetMessageEntity
import com.wire.kalium.persistence.kaliumLogger
import io.mockative.Mockable
import kotlinx.datetime.Instant
import kotlin.coroutines.CoroutineContext

/**
 * Cursor for keyset-based pagination of messages.
 * @param date The creation date of the last message in the current page
 * @param id The ID of the last message in the current page (for tie-breaking)
 */
data class MessageCursor(
    val date: Instant,
    val id: String
)

@Mockable
interface MessageExtensions {
    fun getPagerForConversation(
        conversationId: ConversationIDEntity,
        visibilities: Collection<MessageEntity.Visibility>,
        pagingConfig: PagingConfig,
    ): KaliumKeySetPager<MessageCursor, MessageEntity>

    fun getPagerForMessagesSearch(
        searchQuery: String,
        conversationId: ConversationIDEntity,
        pagingConfig: PagingConfig,
    ): KaliumKeySetPager<MessageCursor, MessageEntity>

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
    ): KaliumKeySetPager<MessageCursor, MessageEntity> {
        // We could return a Flow directly, but having the PagingSource is the only way to test this
        return KaliumKeySetPager(
            Pager(pagingConfig) { getPagingSource(conversationId, visibilities) },
            getPagingSource(conversationId, visibilities),
            coroutineContext
        )
    }

    override fun getPagerForMessagesSearch(
        searchQuery: String,
        conversationId: ConversationIDEntity,
        pagingConfig: PagingConfig,
    ): KaliumKeySetPager<MessageCursor, MessageEntity> {
        // We could return a Flow directly, but having the PagingSource is the only way to test this
        return KaliumKeySetPager(
            Pager(pagingConfig) { getMessagesSearchPagingSource(searchQuery, conversationId) },
            getMessagesSearchPagingSource(searchQuery, conversationId),
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
    ) = QueryPagingSource<MessageCursor, MessageEntity>(
        transacter = messagesQueries,
        context = coroutineContext,
        pageBoundariesProvider = { key, limit ->
            kaliumLogger.d("[QueryPagingSource] Fetching boundaries: anchor = $key, limit = $limit")
            messagesQueries.selectMessageBoundariesByConversationIdAndVisibility(
                conversationId = conversationId,
                visibility = visibilities,
                anchorDate = key?.date,
                anchorId = key?.id?: "",
                limit = limit,
                mapper = { date: Instant, id: String -> MessageCursor(date = date, id = id) }
            )
        },
        queryProvider = { beginInclusive, endExclusive ->
            kaliumLogger.d("[QueryPagingSource] Loading [MessageEntity] data: begin = $beginInclusive, end = $endExclusive")
            messagesQueries.selectMessagesBetweenCursors(
                conversationId = conversationId,
                visibility = visibilities,
                beginDate = beginInclusive.date,
                beginId = beginInclusive.id,
                endDate = endExclusive?.date,
                endId = endExclusive?.id?: "", // in the query if date is null the endId is not used
                mapper = messageMapper::toEntityMessageFromView
            )
        }
    )

    private fun getMessagesSearchPagingSource(
        searchQuery: String,
        conversationId: ConversationIDEntity,
    ) = QueryPagingSource<MessageCursor, MessageEntity>(
        transacter = messagesQueries,
        context = coroutineContext,
        pageBoundariesProvider = { anchor, limit ->
            kaliumLogger.d("[QueryPagingSource] Fetching search boundaries: anchor = $anchor, limit = $limit")
            messagesQueries.selectSearchMessageBoundaries(
                searchQuery = searchQuery,
                conversationId = conversationId,
                anchorDate = anchor?.date,
                anchorId = anchor?.id?: "", // in the query if date is null the anchorId is not used
                limit = limit,
                mapper = { date: Instant, id: String -> MessageCursor(date = date, id = id) }
            )
        },
        queryProvider = { beginInclusive, endExclusive ->
            kaliumLogger.d("[QueryPagingSource] Loading search [MessageEntity] data: begin = $beginInclusive, end = $endExclusive")
            messagesQueries.selectSearchMessagesBetweenCursors(
                searchQuery = searchQuery,
                conversationId = conversationId,
                beginDate = beginInclusive.date,
                beginId = beginInclusive.id,
                endDate = endExclusive?.date,
                endId = endExclusive?.id?: "", // in the query if date is null the endExclusive is not used
                mapper = messageMapper::toEntityMessageFromView
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
