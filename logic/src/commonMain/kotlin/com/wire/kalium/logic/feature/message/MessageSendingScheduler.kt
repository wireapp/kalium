/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.sync.PendingMessagesSenderWorker
import io.mockative.Mockable

/**
 * Responsible for [scheduleSendingOfPendingMessages].
 */
@Mockable
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
    fun scheduleSendingOfPendingMessages()

    /**
     * Cancels the scheduled execution of [PendingMessagesSenderWorker], which attempts to send
     *  all pending messages of this user, because the account has been logged out for instance.
     */
    fun cancelScheduledSendingOfPendingMessages()
}
