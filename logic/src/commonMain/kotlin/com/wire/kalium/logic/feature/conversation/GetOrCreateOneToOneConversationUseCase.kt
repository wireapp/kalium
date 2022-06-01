package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.Member
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold

class GetOrCreateOneToOneConversationUseCase(
    private val conversationRepository: ConversationRepository,
) {

    suspend operator fun invoke(otherUserId: UserId): CreateConversationResult {
        return conversationRepository.getOneToOneConversationDetailsByUserId(otherUserId)
            .fold({ getConversationFailure ->
                if (getConversationFailure is StorageFailure.DataNotFound) {
                    conversationRepository.createGroupConversation(members = listOf(Member(otherUserId)))
                        .fold(
                            CreateConversationResult::Failure,
                            CreateConversationResult::Success
                        )
                } else {
                    CreateConversationResult.Failure(getConversationFailure)
                }
            }, { conversationDetails ->
                CreateConversationResult.Success(conversationDetails.conversation)
            })
    }

}

sealed class CreateConversationResult {
    data class Success(val conversation: Conversation) : CreateConversationResult()
    data class Failure(val coreFailure: CoreFailure) : CreateConversationResult()
}
