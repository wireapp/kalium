/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.fakes

import app.cash.paging.PagingConfig
import app.cash.paging.PagingData
import com.wire.kalium.logic.data.asset.AssetMessage
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageRepositoryExtensions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

internal open class FakeMessageRepositoryExtensions : MessageRepositoryExtensions {
    override suspend fun getPaginatedMessagesByConversationIdAndVisibility(
        conversationId: ConversationId,
        visibility: List<Message.Visibility>,
        pagingConfig: PagingConfig,
        startingOffset: Long
    ): Flow<PagingData<Message.Standalone>> = emptyFlow()

    override suspend fun getPaginatedMessagesSearchBySearchQueryAndConversationId(
        searchQuery: String,
        conversationId: ConversationId,
        pagingConfig: PagingConfig,
        startingOffset: Long
    ): Flow<PagingData<Message.Standalone>> = emptyFlow()

    override suspend fun getPaginatedMessageAssetsWithoutImageByConversationId(
        conversationId: ConversationId,
        pagingConfig: PagingConfig,
        startingOffset: Long
    ): Flow<PagingData<Message.Standalone>> = emptyFlow()

    override suspend fun observePaginatedMessageAssetImageByConversationId(
        conversationId: ConversationId,
        pagingConfig: PagingConfig,
        startingOffset: Long
    ): Flow<PagingData<AssetMessage>> = emptyFlow()
}
