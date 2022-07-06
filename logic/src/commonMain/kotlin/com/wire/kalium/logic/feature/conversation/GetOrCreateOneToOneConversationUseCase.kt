package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold

class GetOrCreateOneToOneConversationUseCase(
    private val conversationRepository: ConversationRepository
) {

    suspend operator fun invoke(otherUserId: UserId): CreateConversationResult {
        // TODO: filter out self user from the list (just in case of client bug that leads to self user to be included part of the list)
        return conversationRepository.getOneToOneConversationDetailsByUserId(otherUserId)
            .fold({ conversationFailure ->
                if (conversationFailure is StorageFailure.DataNotFound) {
                    conversationRepository.createGroupConversation(usersList = listOf(otherUserId))
                        .fold(
                            CreateConversationResult::Failure,
                            CreateConversationResult::Success
                        )
                } else {
                    CreateConversationResult.Failure(conversationFailure)
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
