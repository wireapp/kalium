package com.wire.kalium.logic.feature.conversation

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.data.conversation.MemberChangeResult
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
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
    private val conversationGroupRepository: ConversationGroupRepository,
    private val selfUserId: UserId,
    private val persistMessage: PersistMessageUseCase
) : AddMemberToConversationUseCase {
    override suspend fun invoke(conversationId: ConversationId, userIdList: List<UserId>): AddMemberToConversationUseCase.Result {
        return conversationGroupRepository.addMembers(userIdList, conversationId).fold({
            AddMemberToConversationUseCase.Result.Failure(it)
        }, {
            if (it is MemberChangeResult.Changed) {
                /*
                Backend doesn't forward a member-join message event to the client that add users to a conversation but everyone
                else on the group. Therefore, we need to map the member add api response manually, and create and persist the
                member-join system message on these cases
                 */
                val message = Message.System(
                    id = uuid4().toString(), // We generate a random uuid for this new system message
                    content = MessageContent.MemberChange.Added(members = userIdList),
                    conversationId = conversationId,
                    date = it.time,
                    senderUserId = selfUserId,
                    status = Message.Status.SENT,
                    visibility = Message.Visibility.VISIBLE
                )
                persistMessage(message)
            }
            AddMemberToConversationUseCase.Result.Success
        })
    }
}
