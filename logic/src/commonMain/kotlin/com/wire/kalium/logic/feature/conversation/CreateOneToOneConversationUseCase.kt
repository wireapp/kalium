package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.suspending

class CreateOneToOneConversationUseCase(
    private val conversationRepository: ConversationRepository,
) {

    suspend operator fun invoke(otherUserId: UserId): CreateConversationResult {
        return suspending {
            conversationRepository.getOne2OneConversationByUserId(otherUserId).flatMap { conversationId ->
                if (conversationId != null) {
                    Either.Right(conversationId)
                } else {
                    conversationRepository.createOne2OneConversationWithTeamMate(otherUserId)
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
