package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.id.ConversationId

/**
 * Responsible for [scheduleSendingOfPersistedMessage].
 */
interface MessageSendingScheduler {

    /**
     *  Given [conversationID] and [messageUuid] of a persisted message, schedule the sending/retry.
     *
     *  **When** it's gonna to be executed may vary depending on the platform and/or implementation.
     *
     *  One of the criteria in order to attempt sending a message is that there's
     *  an established internet connection. So the scheduler *may* take this into consideration.
     *
     *  If the implementation is unable to schedule (due to platform limitations for example),
     *  it's OK to just don't do anything, ideally logging a warning about the lack of implementation.
     */
    suspend fun scheduleSendingOfPersistedMessage(conversationID: ConversationId, messageUuid: String)
}

expect class MessageSendingSchedulerImpl(): MessageSendingScheduler
