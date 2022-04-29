package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.sync.PendingMessagesSenderWorker

/**
 * Responsible for [scheduleSendingOfPendingMessages].
 */
interface MessageSendingScheduler {

    /**
     *  Schedules an execution of [PendingMessagesSenderWorker], which attempts to send
     *  all pending messages of this user.
     *
     *  **When** it's gonna to be executed may vary depending on the platform and/or implementation.
     *
     *  One of the criteria in order to attempt sending a message is that there's
     *  an established internet connection. So the scheduler *may* take this into consideration.
     *
     *  If the implementation is unable to schedule (due to platform limitations for example),
     *  it's OK to just don't do anything, ideally logging a warning about the lack of implementation.
     */
    suspend fun scheduleSendingOfPendingMessages()
}
