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
package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageRepository

interface GetConversationMessagesFromSearchQueryUseCase {

    suspend operator fun invoke(searchQuery: String, conversationId: ConversationId)
}

internal class GetConversationMessagesFromSearchQueryUseCaseImpl internal constructor(
    private val messageRepository: MessageRepository
) : GetConversationMessagesFromSearchQueryUseCase {

    override suspend fun invoke(
        searchQuery: String,
        conversationId: ConversationId
    ) {
        messageRepository.getConversationMessagesFromSearch(
            searchQuery = searchQuery,
            conversationId = conversationId
        )
    }
}


