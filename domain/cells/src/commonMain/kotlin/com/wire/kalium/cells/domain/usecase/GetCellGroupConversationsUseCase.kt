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
import com.wire.kalium.cells.domain.model.CellConversation
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either

/**
 * Use case to get the list of conversations that have cells enabled.
 */
public interface GetCellGroupConversationsUseCase {
    public suspend operator fun invoke(): GetCellGroupConversationsUseCaseResult
}

/**
 * Implementation of [GetCellGroupConversationsUseCase] that retrieves the list of conversations
 * including their channel status and access level from the repository and maps them
 * to [CellConversation] objects. The use case returns a [GetCellGroupConversationsUseCaseResult]
 * containing a list of [CellConversation] objects on success, or a [CoreFailure] on failure.
 *
 * @param conversationRepository The repository to fetch the conversation data.
 */
internal class GetCellGroupConversationsUseCaseImpl(
    private val conversationRepository: CellConversationRepository,
) : GetCellGroupConversationsUseCase {

    override suspend operator fun invoke(): GetCellGroupConversationsUseCaseResult {
        return when (val result = conversationRepository.getCellGroupConversations()) {
            is Either.Right -> GetCellGroupConversationsUseCaseResult.Success(result.value)
            is Either.Left -> GetCellGroupConversationsUseCaseResult.Failure(result.value)
        }
    }
}

public sealed class GetCellGroupConversationsUseCaseResult {
    public data class Success(val conversations: List<CellConversation>) : GetCellGroupConversationsUseCaseResult()
    public data class Failure(val failure: CoreFailure) : GetCellGroupConversationsUseCaseResult()
}
