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

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import kotlinx.coroutines.flow.Flow

/**
 * This use case will observe and return the conversation list for the current user.
 * Prefer using [ObserveConversationListDetailsUseCase] instead, since is performance efficient, relying on sql views.
 *
 * @see Conversation
 * @see ObserveConversationDetailsUseCase
 */
// todo(interface). extract interface for use case
public class GetConversationsUseCase internal constructor(
    private val conversationRepository: ConversationRepository
) {

    public sealed class Result {
        public data class Success(val convFlow: Flow<List<Conversation>>) : Result()
        public data class Failure(val storageFailure: StorageFailure) : Result()
    }

    public suspend operator fun invoke(): Result {
        return conversationRepository.getConversationList().fold({
            Result.Failure(it)
        }, {
            Result.Success(it)
        })
    }
}
