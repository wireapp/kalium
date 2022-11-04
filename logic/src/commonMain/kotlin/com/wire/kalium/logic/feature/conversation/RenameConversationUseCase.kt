package com.wire.kalium.logic.feature.conversation

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.fold
import kotlinx.datetime.Clock

/**
 * Renames a conversation by it's ID.
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
                generateSystemMessage(conversationId, conversationName)
                RenamingResult.Success
            })
    }

    private suspend fun generateSystemMessage(conversationId: ConversationId, conversationName: String): Either<CoreFailure, Unit> {
        val message = Message.System(
            id = uuid4().toString(),
            conversationId = conversationId,
            content = MessageContent.ConversationRenamed(conversationName),
            date = Clock.System.now().toString(),
            senderUserId = selfUserId,
            status = Message.Status.SENT,
            visibility = Message.Visibility.VISIBLE
        )
        return persistMessage(message)
    }
}

sealed class RenamingResult {
    object Success : RenamingResult()
    data class Failure(val coreFailure: CoreFailure) : RenamingResult()
}
