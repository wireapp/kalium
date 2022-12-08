package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl

/**
 * Mark decrypted messages with errors as resolved.
 * This should be called after resetting encryption session.
 */
interface ResolveFailedDecryptedMessagesUseCase {

    /**
     * @param conversationId the conversation id to resolve messages
     */
    suspend operator fun invoke(conversationId: ConversationId): ResolveDecryptedErrorResult
}

internal class ResolveFailedDecryptedMessagesUseCaseImpl(
    private val messageRepository: MessageRepository,
    private val kaliumDispatcher: KaliumDispatcher = KaliumDispatcherImpl
) :
    ResolveFailedDecryptedMessagesUseCase {
    override suspend fun invoke(conversationId: ConversationId): ResolveDecryptedErrorResult {
        TODO("Not yet implemented")
    }

}

sealed class ResolveDecryptedErrorResult {
    object Success : ResolveDecryptedErrorResult()
    data class Failure(val coreFailure: CoreFailure) : ResolveDecryptedErrorResult()
}
