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

package com.wire.kalium.logic.sync

import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.SYNC
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger

/**
 * This worker attempts to send all pending messages created by this user.
 * @see [PendingMessagesSenderWorker.doWork]
 */
internal class PendingMessagesSenderWorker(
    private val messageRepository: MessageRepository,
    private val messageSender: MessageSender,
    private val userId: UserId
) : DefaultWorker {

    /**
     * Attempt to send all pending messages for the user.
     *
     * @return [Result.Success]. Can't touch this.
     *
     * Does **not** return [Result.Retry] **nor** [Result.Failure].
     *
     * The failure or retry logic is handled by [MessageSender] for each message.
     */
    override suspend fun doWork(): Result {
        messageRepository.getAllPendingMessagesFromUser(userId)
            .onSuccess { pendingMessages ->
                pendingMessages.forEach { message ->
                    kaliumLogger.withFeatureId(SYNC).i("Attempting scheduled sending of message $message")
                    messageSender.sendPendingMessage(message.conversationId, message.id)
                }
            }.onFailure {
                kaliumLogger.withFeatureId(SYNC).w("Failed to fetch and attempt retry of pending messages: $it")
                // This execution doesn't care about failures
            }

        return Result.Success
    }

}
