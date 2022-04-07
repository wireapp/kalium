package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapMerge

class CreateOneToOneConversationUseCase(
    private val conversationRepository: ConversationRepository,
) {

    suspend operator fun invoke(otherUserId: UserId): CreateConversationResult {
        val conversation = conversationRepository.observeConversationList()
            .flatMapMerge { it.asFlow() }
            .flatMapMerge { conversationRepository.getConversationDetailsById(it.id) }
            .filterIsInstance<ConversationDetails.OneOne>()
            .firstOrNull { otherUserId == it.otherUser.id }

        return if (conversation != null) {
            CreateConversationResult.Success(conversation.conversation.id)
        } else {
            conversationRepository.createOne2OneConversationWithTeamMate(otherUserId).fold({ failure ->
                CreateConversationResult.Failure(failure)
            }, { conversationId ->
                CreateConversationResult.Success(conversationId)
            })
        }
    }

}

sealed class CreateConversationResult {
    data class Success(val conversationId: ConversationId) : CreateConversationResult()
    data class Failure(val coreFailure: CoreFailure) : CreateConversationResult()
}
