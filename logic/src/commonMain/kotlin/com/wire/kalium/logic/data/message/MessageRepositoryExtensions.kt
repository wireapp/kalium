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

package com.wire.kalium.logic.data.message

import app.cash.paging.PagingConfig
import app.cash.paging.PagingData
import app.cash.paging.map
import com.wire.kalium.logic.data.asset.AssetMessage
import com.wire.kalium.logic.data.asset.SUPPORTED_IMAGE_ASSET_MIME_TYPES
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.persistence.dao.asset.AssetMessageEntity
import com.wire.kalium.persistence.dao.message.KaliumPager
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal interface MessageRepositoryExtensions {
    suspend fun getPaginatedMessagesByConversationIdAndVisibility(
        conversationId: ConversationId,
        visibility: List<Message.Visibility>,
        pagingConfig: PagingConfig,
        startingOffset: Long
    ): Flow<PagingData<Message.Standalone>>

    suspend fun getPaginatedMessagesSearchBySearchQueryAndConversationId(
        searchQuery: String,
        conversationId: ConversationId,
        pagingConfig: PagingConfig,
        startingOffset: Long
    ): Flow<PagingData<Message.Standalone>>

    suspend fun getPaginatedMessageAssetsWithoutImageByConversationId(
        conversationId: ConversationId,
        pagingConfig: PagingConfig,
        startingOffset: Long
    ): Flow<PagingData<Message.Standalone>>

    suspend fun observePaginatedMessageAssetImageByConversationId(
        conversationId: ConversationId,
        pagingConfig: PagingConfig,
        startingOffset: Long
    ): Flow<PagingData<AssetMessage>>
}

internal class MessageRepositoryExtensionsImpl internal constructor(
    private val messageDAO: MessageDAO,
    private val messageMapper: MessageMapper,
) : MessageRepositoryExtensions {

    override suspend fun getPaginatedMessagesByConversationIdAndVisibility(
        conversationId: ConversationId,
        visibility: List<Message.Visibility>,
        pagingConfig: PagingConfig,
        startingOffset: Long
    ): Flow<PagingData<Message.Standalone>> {
        val pager: KaliumPager<MessageEntity> = messageDAO.platformExtensions.getPagerForConversation(
            conversationId.toDao(),
            visibility.map { it.toEntityVisibility() },
            pagingConfig,
            startingOffset
        )

        return pager.pagingDataFlow.map {
            it.map { messageMapper.fromEntityToMessage(it) }
        }
    }

    override suspend fun getPaginatedMessagesSearchBySearchQueryAndConversationId(
        searchQuery: String,
        conversationId: ConversationId,
        pagingConfig: PagingConfig,
        startingOffset: Long
    ): Flow<PagingData<Message.Standalone>> {
        val pager: KaliumPager<MessageEntity> = messageDAO.platformExtensions.getPagerForMessagesSearch(
            searchQuery = searchQuery,
            conversationId = conversationId.toDao(),
            pagingConfig = pagingConfig,
            startingOffset = startingOffset
        )

        return pager.pagingDataFlow.map {
            it.map { messageMapper.fromEntityToMessage(it) }
        }
    }

    override suspend fun getPaginatedMessageAssetsWithoutImageByConversationId(
        conversationId: ConversationId,
        pagingConfig: PagingConfig,
        startingOffset: Long
    ): Flow<PagingData<Message.Standalone>> {
        val pager: KaliumPager<MessageEntity> = messageDAO.platformExtensions.getPagerForMessageAssetsWithoutImage(
            conversationId = conversationId.toDao(),
            mimeTypes = SUPPORTED_IMAGE_ASSET_MIME_TYPES,
            pagingConfig = pagingConfig,
            startingOffset = startingOffset
        )

        return pager.pagingDataFlow.map {
            it.map { messageEntity -> messageMapper.fromEntityToMessage(messageEntity) }
        }
    }

    override suspend fun observePaginatedMessageAssetImageByConversationId(
        conversationId: ConversationId,
        pagingConfig: PagingConfig,
        startingOffset: Long
    ): Flow<PagingData<AssetMessage>> {
        val pager: KaliumPager<AssetMessageEntity> = messageDAO.platformExtensions.getPagerForMessageAssetImage(
            conversationId = conversationId.toDao(),
            mimeTypes = SUPPORTED_IMAGE_ASSET_MIME_TYPES,
            pagingConfig = pagingConfig,
            startingOffset = startingOffset
        )

        return pager.pagingDataFlow.map {
            it.map { messageEntity -> messageMapper.fromAssetEntityToAssetMessage(messageEntity) }
        }
    }
}
