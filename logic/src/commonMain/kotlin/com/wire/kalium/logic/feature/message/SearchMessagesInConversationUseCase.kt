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
package com.wire.kalium.logic.feature.message

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * Use case for searching messages in a conversation by text content.
 */
public interface SearchMessagesInConversationUseCase {

    /**
     * Search messages in a conversation by text content.
     * @param conversationId The conversation to search in
     * @param searchQuery The text to search for (case-insensitive, partial match)
     * @param limit Maximum number of results to return (default 100)
     * @param offset Offset for pagination (default 0)
     * @return [Result] with list of matching messages or failure
     */
    public suspend operator fun invoke(
        conversationId: ConversationId,
        searchQuery: String,
        limit: Int = 100,
        offset: Int = 0
    ): Result

    public sealed interface Result {
        public data class Success(val messages: List<Message.Standalone>) : Result
        public data class Failure(val cause: CoreFailure) : Result
    }
}

internal class SearchMessagesInConversationUseCaseImpl internal constructor(
    private val messageRepository: MessageRepository,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : SearchMessagesInConversationUseCase {

    override suspend fun invoke(
        conversationId: ConversationId,
        searchQuery: String,
        limit: Int,
        offset: Int
    ): SearchMessagesInConversationUseCase.Result = withContext(dispatcher.io) {
        messageRepository.searchMessagesByText(
            conversationId = conversationId,
            searchQuery = searchQuery,
            limit = limit,
            offset = offset
        ).fold(
            { SearchMessagesInConversationUseCase.Result.Failure(it) },
            { SearchMessagesInConversationUseCase.Result.Success(it) }
        )
    }
}
