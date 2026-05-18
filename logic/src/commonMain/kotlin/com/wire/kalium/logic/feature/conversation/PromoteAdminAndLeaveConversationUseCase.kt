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

package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId

public interface PromoteAdminAndLeaveConversationUseCase {
    /**
     * Promotes the given [userId] to admin role in [conversationId], then removes the self user from that conversation.
     *
     * @return [Result.Success] if both operations succeed,
     *         [Result.FailedToPromoteUser] if the promotion fails (leave is not attempted),
     *         [Result.FailedToLeaveConversation] if promotion succeeds but leave fails.
     */
    public suspend operator fun invoke(
        conversationId: ConversationId,
        userId: UserId,
    ): Result

    public sealed interface Result {
        public data object Success : Result
        public data object FailedToPromoteUser : Result
        public data object FailedToLeaveConversation : Result
    }
}

internal class PromoteAdminAndLeaveConversationUseCaseImpl(
    private val updateConversationMemberRole: UpdateConversationMemberRoleUseCase,
    private val leaveConversation: LeaveConversationUseCase,
) : PromoteAdminAndLeaveConversationUseCase {

    @Suppress("ReturnCount")
    override suspend fun invoke(
        conversationId: ConversationId,
        userId: UserId,
    ): PromoteAdminAndLeaveConversationUseCase.Result {
        val promoteResult = updateConversationMemberRole(conversationId, userId, Conversation.Member.Role.Admin)
        if (promoteResult is UpdateConversationMemberRoleResult.Failure) {
            return PromoteAdminAndLeaveConversationUseCase.Result.FailedToPromoteUser
        }

        val leaveResult = leaveConversation(conversationId)
        if (leaveResult is RemoveMemberFromConversationUseCase.Result.Failure) {
            return PromoteAdminAndLeaveConversationUseCase.Result.FailedToLeaveConversation
        }

        return PromoteAdminAndLeaveConversationUseCase.Result.Success
    }
}
