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
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger

interface UpdateConversationMemberRoleUseCase {
    /**
     * Use case that allows a conversation member to change its role to:
     * [Member.Role.Admin] or [Member.Role.Member]
     *
     * @param conversationId the id of the conversation where status wants to be changed
     * @param role new status to set the given conversation
     * @return an [ConversationUpdateStatusResult] containing Success or Failure cases
     */
    suspend operator fun invoke(
        conversationId: ConversationId,
        userId: UserId,
        role: Conversation.Member.Role
    ): UpdateConversationMemberRoleResult
}

internal class UpdateConversationMemberRoleUseCaseImpl(
    private val conversationRepository: ConversationRepository
) : UpdateConversationMemberRoleUseCase {

    override suspend fun invoke(
        conversationId: ConversationId,
        userId: UserId,
        role: Conversation.Member.Role
    ): UpdateConversationMemberRoleResult =
        conversationRepository.updateConversationMemberRole(conversationId, userId, role)
            .fold({
                kaliumLogger.e(
                    "Something went wrong when updating the role of user:$userId" +
                            "in conversation:$conversationId to $role"
                )
                UpdateConversationMemberRoleResult.Failure
            }, {
                UpdateConversationMemberRoleResult.Success
            })
}

sealed class UpdateConversationMemberRoleResult {
    data object Success : UpdateConversationMemberRoleResult()
    data object Failure : UpdateConversationMemberRoleResult()
}
