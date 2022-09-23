package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserId

interface LeaveConversationUseCase {

    /**
     * This use case will allow to leave a given group conversation while still keeping the mentioned conversation in
     * the DB.
     *
     * @param conversationId of the group conversation to leave.
     * @return [Result] indicating operation succeeded or if anything failed while removing the user from the conversation.
     */
    suspend operator fun invoke(conversationId: ConversationId): RemoveMemberFromConversationUseCase.Result
}

class LeaveConversationUseCaseImpl(
    private val conversationRepository: ConversationRepository,
    private val selfUserId: UserId,
    private val persistMessage: PersistMessageUseCase
) : LeaveConversationUseCase {
    override suspend fun invoke(conversationId: ConversationId): RemoveMemberFromConversationUseCase.Result {
        // Call the endpoint to delete the member from given conversation and remove the members connection from DB
        return RemoveMemberFromConversationUseCaseImpl(conversationRepository, selfUserId, persistMessage).invoke(
            conversationId,
            selfUserId
        )
    }
}
