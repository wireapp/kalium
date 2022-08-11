package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold

fun interface LeaveGroupConversationUseCase {

    /**
     * This use case will allow leaving a given group conversation delete a conversation for everyone in the group
     *
     * @param conversationId of the group conversation to leave.
     * @return [LeaveGroupResult] indicating operation succeeded or if it failed to find the given conversation on the DB.
     */
    suspend operator fun invoke(conversationId: ConversationId): LeaveGroupResult
}

class LeaveGroupConversationUseCaseImpl(
    private val conversationRepository: ConversationRepository,
    private val selfUserId: UserId
) : LeaveGroupConversationUseCase {

    override suspend operator fun invoke(conversationId: ConversationId): LeaveGroupResult {
        return conversationRepository.updateRemovedBy(conversationId, selfUserId).fold({
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
