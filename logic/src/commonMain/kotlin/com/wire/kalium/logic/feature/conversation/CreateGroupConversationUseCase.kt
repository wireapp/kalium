package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.CreateConversationParam
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.sync.SyncManager
import kotlinx.datetime.Clock

class CreateGroupConversationUseCase(
    private val conversationRepository: ConversationRepository,
    private val syncManager: SyncManager
) {
    suspend operator fun invoke(param: CreateConversationParam): Either<CoreFailure, Conversation> {
        syncManager.waitUntilLive()
        return conversationRepository.createGroupConversation(param).flatMap { conversation ->
            conversationRepository.updateConversationModifiedDate(conversation.id, Clock.System.now().toString()).map { conversation }
        }
    }
}
