/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.messaging.sending

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import io.mockative.Mockable

/**
 * Responsible for orchestrating all the pieces necessary
 * for sending a message to the wanted recipients.
 * Will handle reading and updating message status, retries
 * in case of connectivity issues, and encryption based on
 * [ConversationOptions.Protocol].
 *
 * @see MessageSenderImpl
 */
@Mockable
interface MessageSender {
    /**
     * Given the [ConversationId] and UUID of a message that
     * was previously persisted locally,
     * attempts to send the message to suitable recipients.
     *
     * Will handle all the needed encryption and possible set-up
     * steps and retries depending on the [ConversationOptions.Protocol].
     *
     * In case of connectivity failure, will handle the error by updating the state of the persisted message
     * and, if needed, also scheduling a retry in the future using a [MessageSendingScheduler].
     *
     * @param conversationId
     * @param messageUuid
     */
    suspend fun sendPendingMessage(conversationId: ConversationId, messageUuid: String): Either<CoreFailure, Unit>

    /**
     * Attempts to send the given [Message] to suitable recipients.
     *
     * Will handle all the needed encryption and possible set-up
     * steps and retries depending on the [ConversationOptions.Protocol].
     *
     * Unlike [sendPendingMessage], will **not** handle connectivity failures
     * and scheduling re-tries in the future.
     * Suitable for fire-and-forget messages, like real-time calling signaling,
     * or messages where retrying later is useless or would lead to unwanted behaviour.
     *
     * @param message that will be sent
     * @see [sendPendingMessage]
     */
    suspend fun sendMessage(
        message: Message.Sendable,
        messageTarget: MessageTarget = MessageTarget.Conversation()
    ): Either<CoreFailure, Unit>

    /**
     * Attempts to send the given [BroadcastMessage] to suitable recipients.
     *
     * Will handle all the needed encryption and possible set-up
     * steps
     *
     * Will **not** handle connectivity failures and scheduling re-tries in the future.
     * Suitable for fire-and-forget messages that are not belong to any specific Conversation,
     * like changing user availability status.
     *
     */
    suspend fun broadcastMessage(
        message: BroadcastMessage,
        target: BroadcastMessageTarget
    ): Either<CoreFailure, Unit>

}
