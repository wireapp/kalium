package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.QualifiedID

class UpdateConversationSeenDateUseCase(private val conversationRepository: ConversationRepository) {

    suspend operator fun invoke(conversationId: QualifiedID, timeStamp: String) {
        conversationRepository.updateConversationSeenDate(conversationId, timeStamp)
    }

}
