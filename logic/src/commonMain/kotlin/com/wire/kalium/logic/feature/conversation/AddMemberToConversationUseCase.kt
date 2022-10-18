package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold

interface AddMemberToConversationUseCase {
    suspend operator fun invoke(conversationId: ConversationId, userIdList: List<UserId>): Result

    sealed interface Result {
        object Success : Result
        data class Failure(val cause: CoreFailure) : Result
    }
}

class AddMemberToConversationUseCaseImpl(
    private val conversationGroupRepository: ConversationGroupRepository
) : AddMemberToConversationUseCase {
    override suspend fun invoke(conversationId: ConversationId, userIdList: List<UserId>): AddMemberToConversationUseCase.Result {
        return conversationGroupRepository.addMembers(userIdList, conversationId).fold({
            AddMemberToConversationUseCase.Result.Failure(it)
        }, {
            AddMemberToConversationUseCase.Result.Success
        })
    }
}
