package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger

interface RemoveMemberFromConversationUseCase {

    /**
     * This use case will allow to remove a user from a given group conversation while still keeping the mentioned conversation in
     * the DB.
     *
     * @param conversationId of the group conversation to leave.
     * @param userId of the user that will be removed from the conversation.
     * @return [Result] indicating operation succeeded or if anything failed while removing the user from the conversation.
     */
    suspend operator fun invoke(conversationId: ConversationId, userId: UserId): Result
    sealed interface Result {
        object Success : Result
        data class Failure(val cause: CoreFailure) : Result
    }
}

class RemoveMemberFromConversationUseCaseImpl(
    private val conversationRepository: ConversationRepository,
) : RemoveMemberFromConversationUseCase {
    override suspend fun invoke(conversationId: ConversationId, userId: UserId): RemoveMemberFromConversationUseCase.Result {
        // Call the endpoint to delete the member from given conversation and remove the members connection from DB
        return conversationRepository.deleteMember(userId, conversationId).fold({
            RemoveMemberFromConversationUseCase.Result.Failure(it)
        }, {
            RemoveMemberFromConversationUseCase.Result.Success
        })
    }
}
