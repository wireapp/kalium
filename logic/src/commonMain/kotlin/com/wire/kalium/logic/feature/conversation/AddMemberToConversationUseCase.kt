package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * This use case will add a member(s) to a given conversation.
 */
interface AddMemberToConversationUseCase {
    /**
     * @param conversationId the id of the conversation
     * @param userIdList the list of user ids to add to the conversation
     * @return the [Result] indicating a successful operation, otherwise a [CoreFailure]
     */
    suspend operator fun invoke(conversationId: ConversationId, userIdList: List<UserId>): Result

    sealed interface Result {
        object Success : Result
        data class Failure(val cause: CoreFailure) : Result
    }
}

class AddMemberToConversationUseCaseImpl(
    private val conversationGroupRepository: ConversationGroupRepository,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : AddMemberToConversationUseCase {
    override suspend fun invoke(conversationId: ConversationId, userIdList: List<UserId>): AddMemberToConversationUseCase.Result =
        withContext(dispatcher.default) {
            conversationGroupRepository.addMembers(userIdList, conversationId).fold({
                AddMemberToConversationUseCase.Result.Failure(it)
            }, {
                AddMemberToConversationUseCase.Result.Success
            })
        }
}
