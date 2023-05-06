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

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingState
import app.cash.paging.PagingSource
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
    ): PagingSource<Int, MessageEntity> = object : PagingSource<Int, MessageEntity>() {
        override fun getRefreshKey(state: PagingState<Int, MessageEntity>): Int? {
            return state.anchorPosition?.let { anchorPosition ->
                val anchorPage = state.closestPageToPosition(anchorPosition)
                anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
            }
        }

        @Suppress("TooGenericExceptionCaught")
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MessageEntity> {
            val page = params.key ?: 0
            return try {
                val total = messagesQueries.countByConversationIdAndVisibility(
                    conversationId,
                    visibilities
                ).executeAsOne()
                val data = messagesQueries.selectByConversationIdAndVisibility(
                    conversationId,
                    visibilities,
                    limit = params.loadSize.toLong(),
                    offset = page * params.loadSize.toLong(),
                    messageMapper::toEntityMessageFromView
                ).executeAsList()
                // simulate page loading

                LoadResult.Page(
                    data,
                    prevKey = if (page == 0) null else (page - 1) * params.loadSize,
                    nextKey = if (data.isEmpty()) null else (page + 1) * params.loadSize,
                    itemsBefore = (page * params.loadSize),
                    itemsAfter = (total - (page * params.loadSize) - data.size).toInt()
                )
            } catch (e: Exception) {
                LoadResult.Error(e)
            }
        }
    }
}
