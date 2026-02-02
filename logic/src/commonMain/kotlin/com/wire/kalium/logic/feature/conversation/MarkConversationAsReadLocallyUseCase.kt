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

package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import kotlinx.datetime.Instant

/**
 * Use case to immediately update the conversation read date in the local database
 *
 * This is useful for immediately clearing the unread badge when the user
 * leaves a conversation, while the debounced [UpdateConversationReadDateUseCase]
 * handles sending confirmations and syncing to other devices.
 */
public interface MarkConversationAsReadLocallyUseCase {
    /**
     * Updates the conversation read date locally and returns whether
     * the conversation still has unread events.
     *
     * @param conversationId The conversation to mark as read
     * @param time The timestamp to set as the last read date
     * @return [Either.Right] with true if there are still unread events, false otherwise
     *         [Either.Left] with [StorageFailure] if the operation failed
     */
    public suspend operator fun invoke(
        conversationId: ConversationId,
        time: Instant
    ): MarkConversationAsReadResult
}

public sealed class MarkConversationAsReadResult {
    public data class Success(val hasUnreadEvents: Boolean) : MarkConversationAsReadResult()
    public data class Failure(val failure: CoreFailure) : MarkConversationAsReadResult()
}

internal class MarkConversationAsReadLocallyUseCaseImpl internal constructor(
    private val conversationRepository: ConversationRepository
) : MarkConversationAsReadLocallyUseCase {

    override suspend fun invoke(
        conversationId: ConversationId,
        time: Instant
    ): MarkConversationAsReadResult =
        conversationRepository.updateReadDateAndGetHasUnreadEvents(conversationId, time).fold(
            { failure -> MarkConversationAsReadResult.Failure(failure) },
            { hasUnreadEvents -> MarkConversationAsReadResult.Success(hasUnreadEvents) }
        )
}
