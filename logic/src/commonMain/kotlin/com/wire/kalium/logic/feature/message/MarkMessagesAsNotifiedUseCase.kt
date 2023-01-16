package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map
import com.wire.kalium.util.DateTimeUtil.toIsoDateTimeString
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * Marks conversations in one or all conversations as notified, so the notifications for these messages won't show up again.
 * @see GetNotificationsUseCase
 */
class MarkMessagesAsNotifiedUseCase internal constructor(
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) {

    /**
     * @param conversationId the specific conversation that needs to be marked as notified,
     * or null for marking all notifications as notified.
     */
    @Deprecated("This will be removed in order to use a more explicit input", ReplaceWith("invoke(UpdateTarget)"))
    suspend operator fun invoke(conversationId: ConversationId?): Result = withContext(dispatchers.default) {
        if (conversationId == null) {
            invoke(UpdateTarget.AllConversations)
        } else {
            invoke(UpdateTarget.SingleConversation(conversationId))
        }
    }

    /**
     * @param conversationsToUpdate which conversation(s) to be marked as notified.
     */
    suspend operator fun invoke(conversationsToUpdate: UpdateTarget): Result = withContext(dispatchers.default) {
        messageRepository.getInstantOfLatestMessageFromOtherUsers().map {
            it.toIsoDateTimeString()
        }.flatMap { date ->
            when (conversationsToUpdate) {
                UpdateTarget.AllConversations -> conversationRepository.updateAllConversationsNotificationDate(date)

                is UpdateTarget.SingleConversation ->
                    conversationRepository.updateConversationNotificationDate(conversationsToUpdate.conversationId, date)
            }
        }.fold({ Result.Failure(it) }) { Result.Success }
    }

    /**
     * Specifies which conversations should be marked as notified
     */
    sealed interface UpdateTarget {
        /**
         * All conversations should be marked as notified.
         */
        object AllConversations : UpdateTarget

        /**
         * A specific conversation, represented by its [conversationId], should be marked as notified
         */
        data class SingleConversation(val conversationId: ConversationId) : UpdateTarget
    }
}

sealed class Result {
    object Success : Result()
    data class Failure(val storageFailure: StorageFailure) : Result()
}
