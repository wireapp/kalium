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
package com.wire.kalium.conversation.history.domain

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Use case which checks if conversation history is supported for a given conversation.
 */
public interface IsConversationHistorySupportedForConversationUseCase {
    public suspend operator fun invoke(conversationId: ConversationId): Flow<Boolean>
}

public fun IsConversationHistorySupportedForConversationUseCase(
    conversationByIdProvider: suspend (conversationId: ConversationId) -> Flow<Either<StorageFailure, Conversation>>,
    isBuildTimeAllowed: Boolean,
): IsConversationHistorySupportedForConversationUseCase = object : IsConversationHistorySupportedForConversationUseCase {

    override suspend fun invoke(
        conversationId: ConversationId
    ): Flow<Boolean> = conversationByIdProvider(conversationId).map { result ->
        result.fold({ false }) { conversation ->
            // For now, we only care if the conversation is a Channel, and we are also disabling it in build-time for development purposes.
            // In the future we will also check team/server settings.
            conversation.type == Conversation.Type.Group.Channel && isBuildTimeAllowed
        }
    }

}
