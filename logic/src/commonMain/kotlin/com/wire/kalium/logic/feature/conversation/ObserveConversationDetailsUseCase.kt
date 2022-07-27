package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import kotlinx.coroutines.flow.Flow

class ObserveConversationDetailsUseCase(
    private val conversationRepository: ConversationRepository,
) {

    suspend operator fun invoke(conversationId: ConversationId): Flow<ConversationDetails> {
        return conversationRepository.observeConversationDetailsById(conversationId)
    }
}
