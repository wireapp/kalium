package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationOptions
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.Member
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.SyncManager

class CreateGroupConversationUseCase(
    private val conversationRepository: ConversationRepository,
    private val syncManager: SyncManager
) {
    suspend operator fun invoke(name: String, members: List<Member>, options: ConversationOptions): Either<CoreFailure, Conversation> {
        syncManager.waitForSyncToComplete()
        return conversationRepository.createGroupConversation(name, members, options)
    }
}
