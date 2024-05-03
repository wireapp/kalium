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

import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId

interface LeaveConversationUseCase {

    /**
     * This use case will allow the self user to leave a given group conversation while still keeping the mentioned conversation in
     * the DB.
     *
     * @param conversationId of the group conversation to leave.
     * @return [Result] indicating operation succeeded or if anything failed while removing the user from the conversation.
     */
    suspend operator fun invoke(conversationId: ConversationId): RemoveMemberFromConversationUseCase.Result
}

internal class LeaveConversationUseCaseImpl(
    private val conversationGroupRepository: ConversationGroupRepository,
    private val selfUserId: UserId,
) : LeaveConversationUseCase {
    override suspend fun invoke(conversationId: ConversationId): RemoveMemberFromConversationUseCase.Result {
        return RemoveMemberFromConversationUseCaseImpl(conversationGroupRepository).invoke(
            conversationId,
            selfUserId
        )
    }
}
