package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.functional.Either

class SyncConversationsUseCase(private val conversationRepository: ConversationRepository) {

    suspend operator fun invoke(): Either<CoreFailure, Unit> {
        return conversationRepository.fetchConversations()
    }

}
