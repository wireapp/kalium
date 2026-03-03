/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
package com.wire.kalium.cells.domain.usecase

import com.wire.kalium.cells.domain.CellConversationRepository
import com.wire.kalium.cells.domain.model.Conversation
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either

/**
 * Use case to get the list of conversations that have cells enabled.
 */
public interface GetGroupConversationsWithCellEnabledUseCase {
    public suspend operator fun invoke(): GetConversationsUseCaseResult
}

/**
 * Implementation of [GetGroupConversationsWithCellEnabledUseCase] that retrieves the list of conversations
 * including their channel status and access level from the repository and maps them
 * to [Conversation] objects. The use case returns a [GetConversationsUseCaseResult]
 * containing a list of [Conversation] objects on success, or a [CoreFailure] on failure.
 *
 * @param conversationRepository The repository to fetch the conversation data.
 */
internal class GetGroupConversationsWithCellEnabledUseCaseImpl(
    private val conversationRepository: CellConversationRepository,
) : GetGroupConversationsWithCellEnabledUseCase {

    override suspend operator fun invoke(): GetConversationsUseCaseResult {
        return when (val result = conversationRepository.getGroupConversationDetailsWithCellEnabled()) {
            is Either.Right -> GetConversationsUseCaseResult.Success(result.value)
            is Either.Left -> GetConversationsUseCaseResult.Failure(result.value)
        }
    }
}

public sealed class GetConversationsUseCaseResult {
    public data class Success(val conversations: List<Conversation>) : GetConversationsUseCaseResult()
    public data class Failure(val failure: CoreFailure) : GetConversationsUseCaseResult()
}
