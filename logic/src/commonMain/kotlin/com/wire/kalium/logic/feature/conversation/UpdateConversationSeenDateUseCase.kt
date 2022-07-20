package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.QualifiedID
import kotlinx.datetime.Instant

class UpdateConversationSeenDateUseCase(private val conversationRepository: ConversationRepository) {

    suspend operator fun invoke(conversationId: QualifiedID, timestamp: Instant) {
        conversationRepository.updateConversationSeenDate(conversationId, timestamp.toEpochMilliseconds().toString())
    }

}
