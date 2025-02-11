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
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.common.functional.fold
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Operation that fetches all known users which are not a part of a given conversation [conversationId]
 *
 * @param conversationId
 * @return Result with list of known users not being a part of a conversation
 */
class GetAllContactsNotInConversationUseCase internal constructor(
    private val userRepository: UserRepository
) {
    operator fun invoke(conversationId: ConversationId): Flow<Result> =
        userRepository
            .observeAllKnownUsersNotInConversation(conversationId)
            .map { it.fold(Result::Failure, Result::Success) }

}

sealed class Result {
    data class Success(val contactsNotInConversation: List<OtherUser>) : Result()
    data class Failure(val storageFailure: StorageFailure) : Result()
}
