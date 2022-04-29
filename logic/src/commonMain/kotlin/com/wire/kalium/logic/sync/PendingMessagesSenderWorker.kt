package com.wire.kalium.logic.sync

import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.suspending

/**
 * This worker attempts to send all pending messages created by this user.
 * @see [PendingMessagesSenderWorker.doWork]
 */
internal class PendingMessagesSenderWorker(
    private val messageRepository: MessageRepository,
    private val messageSender: MessageSender,
    private val userId: UserId,
    userSessionScope: UserSessionScope
) : UserSessionWorker(userSessionScope) {

    /**
     * Attempt to send all pending messages for the user.
     *
     * @return [Result.Success]. Can't touch this.
     *
     * Does **not** return [Result.Retry] **nor** [Result.Failure].
     *
     * The failure or retry logic is handled by [MessageSender] for each message.
     */
    override suspend fun doWork(): Result = suspending {
        messageRepository.getAllPendingMessagesFromUser(userId).flatMap { pendingMessages ->
            pendingMessages.forEach { message ->
                messageSender.trySendingOutgoingMessageById(message.conversationId, message.id)
            }
            Either.Right(Unit)
        }.coFold({ Result.Success }, { Result.Success })
    }

}
