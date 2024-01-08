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
package com.wire.kalium.logic.feature.asset

import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.wire.kalium.logic.data.asset.AssetMessage
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.util.KaliumDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn

/**
 * This use case will observe and return a flow of paginated image asset messages for a given conversation.
 * @see PagingData
 * @see AssetMessage
 */
class ObservePaginatedAssetImageMessages internal constructor(
    private val dispatcher: KaliumDispatcher,
    private val messageRepository: MessageRepository
) {
    suspend operator fun invoke(
        conversationId: ConversationId,
        startingOffset: Long,
        pagingConfig: PagingConfig
    ): Flow<PagingData<AssetMessage>> = messageRepository.extensions.observePaginatedMessageAssetImageByConversationId(
        conversationId = conversationId,
        pagingConfig = pagingConfig,
        startingOffset = startingOffset
    ).flowOn(dispatcher.io)
}
