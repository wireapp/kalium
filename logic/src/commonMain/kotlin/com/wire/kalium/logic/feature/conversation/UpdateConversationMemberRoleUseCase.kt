package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

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
    private val conversationRepository: ConversationRepository,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : UpdateConversationMemberRoleUseCase {

    override suspend fun invoke(
        conversationId: ConversationId,
        userId: UserId,
        role: Conversation.Member.Role
    ): UpdateConversationMemberRoleResult = withContext(dispatcher.default) {
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
}

sealed class UpdateConversationMemberRoleResult {
    object Success : UpdateConversationMemberRoleResult()
    object Failure : UpdateConversationMemberRoleResult()
}
