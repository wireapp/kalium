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
import app.cash.sqldelight.paging3.QueryPagingSource
import com.wire.kalium.persistence.MessagesQueries
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.kaliumLogger
import kotlin.coroutines.CoroutineContext

actual interface MessageExtensions {
    fun getPagerForConversation(
        conversationId: ConversationIDEntity,
        visibilities: Collection<MessageEntity.Visibility>,
        pagingConfig: PagingConfig,
        startingOffset: Int
    ): KaliumPager<MessageEntity>
}

actual class MessageExtensionsImpl actual constructor(
    private val messagesQueries: MessagesQueries,
    private val messageMapper: MessageMapper,
    private val coroutineContext: CoroutineContext,
) : MessageExtensions {

    override fun getPagerForConversation(
        conversationId: ConversationIDEntity,
        visibilities: Collection<MessageEntity.Visibility>,
        pagingConfig: PagingConfig,
        startingOffset: Int
    ): KaliumPager<MessageEntity> {
        // We could return a Flow directly, but having the PagingSource is the only way to test this
        return KaliumPager(
            Pager(pagingConfig) { getPagingSource(conversationId, visibilities, startingOffset) },
            getPagingSource(conversationId, visibilities, startingOffset),
            coroutineContext
        )
    }

    private fun getPagingSource(
        conversationId: ConversationIDEntity,
        visibilities: Collection<MessageEntity.Visibility>,
        startingOffset: Int
    ) =
        QueryPagingSource(
            countQuery = messagesQueries.countByConversationIdAndVisibility(conversationId, visibilities),
            transacter = messagesQueries,
            context = coroutineContext,
            queryProvider = { limit, offset ->
                kaliumLogger.d("Querying messages for conversation limit=$limit offset=$offset")
                messagesQueries.selectByConversationIdAndVisibility(
                    conversationId,
                    visibilities,
                    limit,
                    offset + startingOffset,
                    messageMapper::toEntityMessageFromView
                )
            }
        )
}
