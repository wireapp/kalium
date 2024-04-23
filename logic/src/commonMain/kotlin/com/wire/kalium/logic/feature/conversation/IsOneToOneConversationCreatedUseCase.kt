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

import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.isRight
import kotlinx.coroutines.flow.first

/**
 * Operation that indicates if we have one-to-one Conversation with specific [UserId].
 *
 * @param otherUserId [UserId] private conversation with which we are interested in.
 * @return true
 */
interface IsOneToOneConversationCreatedUseCase {
    suspend operator fun invoke(otherUserId: UserId): Boolean
}

internal class IsOneToOneConversationCreatedUseCaseImpl(
    private val conversationRepository: ConversationRepository
) : IsOneToOneConversationCreatedUseCase {
    override suspend operator fun invoke(otherUserId: UserId): Boolean {
        return conversationRepository.observeOneToOneConversationWithOtherUser(otherUserId)
            .first()
            .isRight()
    }
}
