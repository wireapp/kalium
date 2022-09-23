package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import kotlinx.coroutines.flow.Flow

fun interface ObserveConversationViewUseCase {
    suspend operator fun invoke(): Flow<List<ConversationDetails>>
}

internal class ObserveConversationViewUseCaseImpl(
    private val conversationRepository: ConversationRepository,
) : ObserveConversationViewUseCase {

    override suspend operator fun invoke(): Flow<List<ConversationDetails>> =
        conversationRepository.observeConversationViewList()
}
