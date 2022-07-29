package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.Member
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
     * @param mutedConversationStatus new status to set the given conversation
     * @return an [ConversationUpdateStatusResult] containing Success or Failure cases
     */
    suspend operator fun invoke(conversationId: ConversationId, userId: UserId, role: Member.Role): UpdateConversationMemberRoleResult
}

internal class UpdateConversationMemberRoleUseCaseImpl(
    private val conversationRepository: ConversationRepository
): UpdateConversationMemberRoleUseCase {

    override suspend fun invoke(conversationId: ConversationId, userId: UserId, role: Member.Role): UpdateConversationMemberRoleResult =
        conversationRepository.updateConversationMemberRole(conversationId, userId, role)
            .fold({
                kaliumLogger.e(
                    "Something went wrong when updating the role of user:$userId in conversation:$conversationId to $role"
                )
                UpdateConversationMemberRoleResult.Failure
            }, {
                UpdateConversationMemberRoleResult.Success
            })
}

sealed class UpdateConversationMemberRoleResult {
    object Success : UpdateConversationMemberRoleResult()
    object Failure : UpdateConversationMemberRoleResult()
}
