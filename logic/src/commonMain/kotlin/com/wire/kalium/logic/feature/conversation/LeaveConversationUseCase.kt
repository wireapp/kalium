package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

interface LeaveConversationUseCase {

    /**
     * This use case will allow the self user to leave a given group conversation while still keeping the mentioned conversation in
     * the DB.
     *
     * @param conversationId of the group conversation to leave.
     * @return [Result] indicating operation succeeded or if anything failed while removing the user from the conversation.
     */
    suspend operator fun invoke(conversationId: ConversationId): RemoveMemberFromConversationUseCase.Result
}

class LeaveConversationUseCaseImpl(
    private val conversationGroupRepository: ConversationGroupRepository,
    private val selfUserId: UserId,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : LeaveConversationUseCase {
    override suspend fun invoke(conversationId: ConversationId): RemoveMemberFromConversationUseCase.Result =
        withContext(dispatcher.default) {
            RemoveMemberFromConversationUseCaseImpl(conversationGroupRepository).invoke(
                conversationId,
                selfUserId
            )
        }
}
