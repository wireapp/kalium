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

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Operation that returns [Conversation] data for the one-to-one conversation with specific [UserId].
 *
 * @param otherUserId [UserId] private conversation with which we are interested in.
 * @return [Result.Success] with [Conversation] in case of success,
 * or [Result.Failure] if something went wrong - can't get data from local DB.
 */
class GetOneToOneConversationUseCase internal constructor(
    private val conversationRepository: ConversationRepository,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) {

    suspend operator fun invoke(otherUserId: UserId): Flow<Result> =
        conversationRepository.observeOneToOneConversationWithOtherUser(otherUserId)
            .map { result -> result.fold({ Result.Failure }, { Result.Success(it) }) }
            .flowOn(dispatchers.io)

    sealed class Result {
        data class Success(val conversation: Conversation) : Result()
        data object Failure : Result()
    }

}
