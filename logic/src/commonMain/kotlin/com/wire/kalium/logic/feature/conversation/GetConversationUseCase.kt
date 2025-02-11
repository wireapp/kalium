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

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.common.functional.fold
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * This use case will observe and return the conversation model for a specific conversation.
 * @see Conversation
 * @see ObserveConversationDetailsUseCase
 */
class GetConversationUseCase(
    private val conversationRepository: ConversationRepository
) {
    sealed class Result {
        data class Success(val conversation: Conversation) : Result()
        data class Failure(val storageFailure: StorageFailure) : Result()
    }

    suspend operator fun invoke(conversationId: QualifiedID): Flow<Result> {
        return conversationRepository.observeConversationById(conversationId)
            .map { it.fold({ Result.Failure(it) }, { Result.Success(it) }) }
    }
}
