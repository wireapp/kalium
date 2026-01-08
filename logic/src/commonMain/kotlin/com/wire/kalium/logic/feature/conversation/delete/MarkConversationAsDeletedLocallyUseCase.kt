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
package com.wire.kalium.logic.feature.conversation.delete

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId

/**
 * Marks a conversation as deleted locally, which is a first step in the local deletion process.
 * After this call, the conversation will not be visible in the app.
 * To complete the local deletion, call [DeleteConversationUseCase], which can already be done asynchronously in the background.
 */
public interface MarkConversationAsDeletedLocallyUseCase {
    public suspend operator fun invoke(conversationId: ConversationId): MarkConversationAsDeletedResult
}

public sealed class MarkConversationAsDeletedResult {
    public data object Success : MarkConversationAsDeletedResult()
    public data class Failure(val failure: CoreFailure) : MarkConversationAsDeletedResult()
}

internal class MarkConversationAsDeletedLocallyUseCaseImpl(
    private val conversationRepository: ConversationRepository,
) : MarkConversationAsDeletedLocallyUseCase {

    override suspend fun invoke(conversationId: ConversationId): MarkConversationAsDeletedResult =
        conversationRepository.markConversationAsDeletedLocally(conversationId).fold(
            { failure -> MarkConversationAsDeletedResult.Failure(failure) }, {
                MarkConversationAsDeletedResult.Success
            }
        )
}
