package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import kotlinx.coroutines.flow.Flow

/**
 * This use case will observe and return the list of conversation details for the current user.
 * @see ConversationDetails
 */
fun interface ObserveConversationListDetailsUseCase {
    suspend operator fun invoke(): Flow<List<ConversationDetails>>
}

internal class ObserveConversationListDetailsUseCaseImpl(
    private val conversationRepository: ConversationRepository,
) : ObserveConversationListDetailsUseCase {

    override suspend operator fun invoke(): Flow<List<ConversationDetails>> =
        conversationRepository.observeConversationListDetails()
}
