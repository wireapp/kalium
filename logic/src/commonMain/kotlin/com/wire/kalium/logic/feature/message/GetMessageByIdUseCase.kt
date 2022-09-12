package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.util.KaliumDispatcher
import kotlinx.coroutines.withContext

/**
 * Provides a way to get a full message using its [ConversationId] and message ID coordinates.
 */
class GetMessageByIdUseCase(
    private val messageRepository: MessageRepository,
    private val dispatchers: KaliumDispatcher
) {

    suspend operator fun invoke(
        conversationId: ConversationId,
        messageId: String
    ): Result = withContext(dispatchers.io) {
        messageRepository.getMessageById(conversationId, messageId).fold({
            Result.Failure(it)
        }, {
            Result.Success(it)
        })
    }

    sealed interface Result {

        data class Success(val message: Message) : Result

        /**
         * [StorageFailure.DataNotFound] or some other generic error.
         */
        data class Failure(val cause: CoreFailure) : Result
    }
}
