package com.wire.kalium.logic.feature.message

import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.MESSAGES
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger

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
    private val messageRepository: MessageRepository
) :
    ResolveFailedDecryptedMessagesUseCase {
    override suspend fun invoke(conversationId: ConversationId): ResolveDecryptedErrorResult =
        messageRepository.markMessagesAsDecryptionResolved(conversationId).fold({
            kaliumLogger.withFeatureId(MESSAGES).e("There was an error marking messages as decryption resolved")
            ResolveDecryptedErrorResult.Failure(it)
        }, {
            ResolveDecryptedErrorResult.Success
        })
}

sealed class ResolveDecryptedErrorResult {
    object Success : ResolveDecryptedErrorResult()
    data class Failure(val coreFailure: CoreFailure) : ResolveDecryptedErrorResult()
}
