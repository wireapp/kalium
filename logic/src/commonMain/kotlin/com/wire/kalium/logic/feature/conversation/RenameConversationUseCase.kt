package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold

/**
 * Renames a conversation by its ID.
 */
interface RenameConversationUseCase {
    /**
     * @param conversationId the conversation id to rename
     * @param conversationName the new conversation name
     */
    suspend operator fun invoke(conversationId: ConversationId, conversationName: String): RenamingResult
}

internal class RenameConversationUseCaseImpl(
    val conversationRepository: ConversationRepository,
    val persistMessage: PersistMessageUseCase,
    val selfUserId: UserId
) : RenameConversationUseCase {
    override suspend fun invoke(conversationId: ConversationId, conversationName: String): RenamingResult {
        return conversationRepository.changeConversationName(conversationId, conversationName)
            .fold({
                RenamingResult.Failure(it)
            }, {
                RenamingResult.Success
            })
    }
}

sealed class RenamingResult {
    object Success : RenamingResult()
    data class Failure(val coreFailure: CoreFailure) : RenamingResult()
}
