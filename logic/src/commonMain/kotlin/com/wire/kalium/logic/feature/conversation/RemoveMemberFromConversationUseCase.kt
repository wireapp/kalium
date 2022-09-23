package com.wire.kalium.logic.feature.conversation

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MemberChangeResult
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold
import kotlinx.datetime.Clock

interface RemoveMemberFromConversationUseCase {

    /**
     * This use case will allow to remove a user from a given group conversation while still keeping the mentioned conversation in
     * the DB.
     *
     * @param conversationId of the group conversation to leave.
     * @param userIdToRemove of the user that will be removed from the conversation.
     * @return [Result] indicating operation succeeded or if anything failed while removing the user from the conversation.
     */
    suspend operator fun invoke(conversationId: ConversationId, userIdToRemove: UserId): Result
    sealed interface Result {
        object Success : Result
        data class Failure(val cause: CoreFailure) : Result
    }
}

class RemoveMemberFromConversationUseCaseImpl(
    private val conversationRepository: ConversationRepository,
    private val selfUserId: UserId,
    private val persistMessage: PersistMessageUseCase
) : RemoveMemberFromConversationUseCase {
    override suspend fun invoke(conversationId: ConversationId, userIdToRemove: UserId): RemoveMemberFromConversationUseCase.Result {
        // Call the endpoint to delete the member from given conversation and remove the members connection from DB
        return conversationRepository.deleteMember(userIdToRemove, conversationId).fold({
            RemoveMemberFromConversationUseCase.Result.Failure(it)
        }, {
            if (it is MemberChangeResult.Changed) {
                // Backend doesn't forward a member-leave message event to clients that remove themselves from a conversation but everyone else
                // on the group. Therefore, we need to map the member deletion api response manually, and create and persist the member-leave
                // system message on these cases
                val message = Message.System(
                    id = uuid4().toString(), // We generate a random uuid for this new system message
                    content = MessageContent.MemberChange.Removed(members = listOf(userIdToRemove)),
                    conversationId = conversationId,
                    date = it.time,
                    senderUserId = selfUserId,
                    status = Message.Status.SENT,
                    visibility = Message.Visibility.VISIBLE
                )
                persistMessage(message)
            }
            RemoveMemberFromConversationUseCase.Result.Success
        })
    }
}
