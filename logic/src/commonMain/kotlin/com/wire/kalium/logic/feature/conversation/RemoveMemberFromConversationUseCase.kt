package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold

interface RemoveMemberFromConversationUseCase {
    suspend operator fun invoke(conversationId: ConversationId, userId: UserId): Result
    sealed interface Result {
        object Success : Result
        data class Failure(val cause: CoreFailure) : Result
    }
}

class RemoveMemberFromConversationUseCaseImpl(
    private val conversationRepository: ConversationRepository,
) : RemoveMemberFromConversationUseCase {
    override suspend fun invoke(
        conversationId: ConversationId,
        userId: UserId
    ): RemoveMemberFromConversationUseCase.Result =
        conversationRepository.deleteMember(userId, conversationId).fold({
            RemoveMemberFromConversationUseCase.Result.Failure(it)
        }, {
            RemoveMemberFromConversationUseCase.Result.Success
        })

}
