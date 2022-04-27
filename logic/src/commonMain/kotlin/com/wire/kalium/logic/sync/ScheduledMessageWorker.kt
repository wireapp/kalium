package com.wire.kalium.logic.sync

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.kaliumLogger

/**
 * Given [conversationId] and [messageUuid], this worker will
 * attempt to send a message that was persisted in the database in the past.
 */
internal class ScheduledMessageWorker(
    private val conversationId: ConversationId,
    private val messageUuid: String,
    userSessionScope: UserSessionScope
) : UserSessionWorker(userSessionScope) {

    /**
     * Attempt to send the message.
     * @return [Result.Success] if everything went smooth or [Result.Retry] in case of failure.
     * Does **not** return [Result.Retry]. The retry logic is handled by [MessageSender].
     */
    override suspend fun doWork(): Result {
        kaliumLogger.i("Scheduled sending of message. ConversationId=$conversationId; Message UUID=$messageUuid")
        return userSessionScope.messages.messageSender.trySendingOutgoingMessageById(
            conversationId, messageUuid
        ).fold({
            Result.Failure
        }, {
            Result.Success
        })
    }

}
