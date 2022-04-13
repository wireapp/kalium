package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.Member
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.suspending

class GetOrCreateOneToOneConversationUseCase(
    private val conversationRepository: ConversationRepository,
) {

    suspend operator fun invoke(otherUserId: UserId): CreateConversationResult {
        return suspending {
            conversationRepository.getOneToOneConversationDetailsByUserId(otherUserId).flatMap { conversation ->
                if (conversation != null) {
                    Either.Right(conversation.conversation)
                } else {
                    conversationRepository.createGroupConversation(members = listOf(Member(otherUserId)))
                }
            }
        }.fold(
            { failure -> CreateConversationResult.Failure(failure) },
            { conversation -> CreateConversationResult.Success(conversation) }
        )
    }

}

sealed class CreateConversationResult {
    data class Success(val conversationId: Conversation) : CreateConversationResult()
    data class Failure(val coreFailure: CoreFailure) : CreateConversationResult()
}
