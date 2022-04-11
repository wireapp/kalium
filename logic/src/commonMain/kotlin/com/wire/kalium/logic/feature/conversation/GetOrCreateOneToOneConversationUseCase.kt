package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
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
            conversationRepository.getOne2OneConversationDetailsByUserId(otherUserId).flatMap { conversation ->
                if (conversation != null) {
                    Either.Right(conversation.conversation.id)
                } else {
                    conversationRepository.createGroupConversation(members = listOf(Member(otherUserId))).map { it.id }
                }
            }
        }.fold(
            { failure -> CreateConversationResult.Failure(failure) },
            { conversationId -> CreateConversationResult.Success(conversationId) }
        )
    }

}

sealed class CreateConversationResult {
    data class Success(val conversationId: ConversationId) : CreateConversationResult()
    data class Failure(val coreFailure: CoreFailure) : CreateConversationResult()
}
