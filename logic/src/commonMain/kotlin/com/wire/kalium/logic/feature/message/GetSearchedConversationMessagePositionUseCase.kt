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

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.functional.fold

/**
 * Gets the Selected Message Position from Search
 *
 * @param conversationId [ConversationId] the id of the conversation where the search is happening.
 * @param messageId [String] the id of the selected message from search.
 *
 * @result [Result] Success with Int position. Failure with StorageFailure.
 */
interface GetSearchedConversationMessagePositionUseCase {

    suspend operator fun invoke(
        conversationId: ConversationId,
        messageId: String
    ): Result

    sealed interface Result {
        data class Success(val position: Int) : Result
        data class Failure(val cause: StorageFailure) : Result
    }
}

internal class GetSearchedConversationMessagePositionUseCaseImpl internal constructor(
    private val messageRepository: MessageRepository
) : GetSearchedConversationMessagePositionUseCase {

    override suspend fun invoke(
        conversationId: ConversationId,
        messageId: String
    ): GetSearchedConversationMessagePositionUseCase.Result = messageRepository
        .getSearchedConversationMessagePosition(
            conversationId = conversationId,
            messageId = messageId
        ).fold(
            { GetSearchedConversationMessagePositionUseCase.Result.Failure(it) },
            { GetSearchedConversationMessagePositionUseCase.Result.Success(it) }
        )
}
