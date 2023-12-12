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
package com.wire.kalium.logic.feature.asset

import com.wire.kalium.logic.data.asset.AssetMessage
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.util.KaliumDispatcher
import kotlinx.coroutines.withContext

interface GetAssetMessagesForConversationUseCase {
    /**
     * This use case will return messages that contains assets (but not image) as content for a given [conversationId]
     * paginated by [limit] and [offset]
     * @see Message.Standalone
     */
    suspend operator fun invoke(
        conversationId: ConversationId,
        limit: Int,
        offset: Int,
    ): List<Message.Standalone>
}

class GetAssetMessagesForConversationUseCaseImpl internal constructor(
    private val dispatcher: KaliumDispatcher,
    private val messageRepository: MessageRepository,
) : GetAssetMessagesForConversationUseCase {

    override suspend operator fun invoke(
        conversationId: ConversationId,
        limit: Int,
        offset: Int,
    ): List<Message.Standalone> = withContext(dispatcher.io) {
        messageRepository.getAssetMessagesByConversationId(
            conversationId = conversationId,
            limit = limit,
            offset = offset
        )
    }
}
