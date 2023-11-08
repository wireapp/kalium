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

package com.wire.kalium.logic.data.message

import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toDao
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
    ): Flow<PagingData<Message.Standalone>>

    suspend fun getPaginatedMessagesByConversationIdAndVisibilityAndContentType(
        conversationId: ConversationId,
        visibility: List<Message.Visibility>,
        contentTypes: List<MessageEntity.ContentType>,
        pagingConfig: PagingConfig,
    ): Flow<PagingData<Message.Standalone>>
}

actual class MessageRepositoryExtensionsImpl actual constructor(
    private val messageDAO: MessageDAO,
    private val messageMapper: MessageMapper,
) : MessageRepositoryExtensions {

    override suspend fun getPaginatedMessagesByConversationIdAndVisibility(
        conversationId: ConversationId,
        visibility: List<Message.Visibility>,
        pagingConfig: PagingConfig,
    ): Flow<PagingData<Message.Standalone>> {
        val pager: KaliumPager<MessageEntity> = messageDAO.platformExtensions.getPagerForConversation(
            conversationId.toDao(),
            visibility.map { it.toEntityVisibility() },
            null,
            pagingConfig
        )

        return pager.pagingDataFlow.map { pagingData: PagingData<MessageEntity> ->
            pagingData.map(messageMapper::fromEntityToMessage)
        }
    }

    override suspend fun getPaginatedMessagesByConversationIdAndVisibilityAndContentType(
        conversationId: ConversationId,
        visibility: List<Message.Visibility>,
        contentTypes: List<MessageEntity.ContentType>,
        pagingConfig: PagingConfig
    ): Flow<PagingData<Message.Standalone>> {
        val pager: KaliumPager<MessageEntity> = messageDAO.platformExtensions.getPagerForConversation(
            conversationId.toDao(),
            visibility.map { it.toEntityVisibility() },
            contentTypes,
            pagingConfig
        )

        return pager.pagingDataFlow.map { pagingData: PagingData<MessageEntity> ->
            pagingData.map(messageMapper::fromEntityToMessage)
        }
    }
}
