package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold

fun interface LeaveGroupConversationUseCase {

    /**
     * This use case will allow leaving a given group conversation delete a conversation for everyone in the group
     *
     * @param conversationId of the group conversation to leave.
     * @param removedBy the user id of the member that removed the self user. If this id matches with the self user id it means that the
     * user left the group voluntarily, otherwise the user was removed by another admin user in the group conversation.
     * If the [removedBy] id is null, that means that the user is no longer removed from the conversation, i.e. it belongs again to it.
     * @return [LeaveGroupResult] indicating operation succeeded or if it failed to find the given conversation on the DB.
     */
    suspend operator fun invoke(conversationId: ConversationId, removedBy: UserId?): LeaveGroupResult
}

internal class LeaveGroupConversationUseCaseImpl(
    private val conversationRepository: ConversationRepository,
) : LeaveGroupConversationUseCase {

    override suspend operator fun invoke(conversationId: ConversationId, removedBy: UserId?): LeaveGroupResult {
        return conversationRepository.updateRemovedBy(conversationId, removedBy).fold({
            LeaveGroupResult.NoConversationFound(it)
        }, {
            LeaveGroupResult.Success
        })
    }
}

sealed class LeaveGroupResult {
    object Success : LeaveGroupResult()
    class NoConversationFound(val coreFailure: CoreFailure) : LeaveGroupResult()
}
