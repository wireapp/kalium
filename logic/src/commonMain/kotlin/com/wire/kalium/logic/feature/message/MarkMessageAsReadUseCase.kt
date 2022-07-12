package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.functional.fold
import kotlinx.datetime.Clock


interface MarkMessageAsReadUseCase {
    suspend operator fun invoke(conversationId: ConversationId, messageId: String)
}

class MarkMessageAsReadUseCaseImpl(private val messageRepository: MessageRepository) : MarkMessageAsReadUseCase {

    override suspend operator fun invoke(conversationId: ConversationId, messageId: String) {
        messageRepository
            .markMessageAsRead(
                conversationId = conversationId,
                messageUuid = messageId,
                timeStamp = Clock.System.now().toEpochMilliseconds()
            )
            .fold({ Result.Failure(it) }, { Result.Success })
    }

    sealed class Result {
        object Success : Result()
        data class Failure(val storageFailure: StorageFailure) : Result()
    }
}


