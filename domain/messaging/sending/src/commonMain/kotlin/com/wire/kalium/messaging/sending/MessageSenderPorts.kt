/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.Recipient
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.BroadcastMessageOption
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageEnvelope
import com.wire.kalium.logic.data.message.MessageSent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.base.authenticated.message.MLSMessageApi
import kotlin.time.Instant

/** Stored messages the sender reads and updates. */
interface OutgoingMessages {
    suspend fun getMessageById(conversationId: ConversationId, messageUuid: String): Either<StorageFailure, Message>

    suspend fun deleteMessage(messageUuid: String, conversationId: ConversationId): Either<CoreFailure, Unit>

    suspend fun updateTextMessage(
        conversationId: ConversationId,
        messageContent: MessageContent.TextEdited,
        newMessageId: String,
        editInstant: Instant
    ): Either<CoreFailure, Unit>

    suspend fun updateMultipartMessage(
        conversationId: ConversationId,
        messageContent: MessageContent.MultipartEdited,
        newMessageId: String,
        editInstant: Instant
    ): Either<CoreFailure, Unit>

    /**
     * updates the message status to [com.wire.kalium.persistence.dao.message.MessageEntity.Status.SENT] and optionally sets
     * the message creation date to [serverDate] if not null, also marks other pending messages and adds millis to their date
     */
    suspend fun promoteMessageToSentUpdatingServerTime(
        conversationId: ConversationId,
        messageUuid: String,
        serverDate: Instant?,
        millis: Long
    ): Either<CoreFailure, Unit>

    suspend fun persistRecipientsDeliveryFailure(
        conversationId: ConversationId,
        messageUuid: String,
        usersWithFailedDeliveryList: List<UserId>
    ): Either<CoreFailure, Unit>

    suspend fun persistNoClientsToDeliverFailure(
        conversationId: ConversationId,
        messageUuid: String,
        usersWithFailedDeliveryList: List<UserId>
    ): Either<CoreFailure, Unit>
}

/** The backend endpoints that deliver messages. */
interface MessageTransport {
    /**
     * Send a Proteus [MessageEnvelope] to the given [conversationId].
     *
     * @return [Either.Right] with the server date time in case of success
     * @return [Either.Left] of a [com.wire.kalium.logic.failure.ProteusSendMessageFailure] if the server rejected the message
     * @return [Either.Left] of other [CoreFailure] for more generic cases
     */
    suspend fun sendEnvelope(
        conversationId: ConversationId,
        envelope: MessageEnvelope,
        messageTarget: MessageTarget,
    ): Either<CoreFailure, MessageSent>

    /**
     * Send a Proteus [MessageEnvelope].
     *
     * @return [Either.Right] with the server date time in case of success
     * @return [Either.Left] of a [com.wire.kalium.logic.failure.ProteusSendMessageFailure] if the server rejected the message
     * @return [Either.Left] of other [CoreFailure] for more generic cases
     */
    suspend fun broadcastEnvelope(
        envelope: MessageEnvelope,
        messageOption: BroadcastMessageOption
    ): Either<CoreFailure, Instant>

    suspend fun sendMLSMessage(message: MLSMessageApi.Message): Either<CoreFailure, MessageSent>

    /**
     * Send a batch of persistent targeted MLS messages.
     *
     * @return [Either.Left] of [com.wire.kalium.common.error.NetworkFailure.FeatureNotSupported] if the
     * backend API version predates targeted messages
     */
    suspend fun sendMLSTargetedMessage(batch: MLSMessageApi.TargetedMessageBatch): Either<CoreFailure, MessageSent>

    /**
     * Send a transient MLS message, which the backend doesn't persist.
     *
     * @return [Either.Left] of [com.wire.kalium.common.error.NetworkFailure.FeatureNotSupported] if the
     * backend API version predates transient messages
     */
    suspend fun sendMLSTransientMessage(message: MLSMessageApi.TransientMessage): Either<CoreFailure, MessageSent>

    /**
     * Send a batch of transient targeted MLS messages, which the backend doesn't persist.
     *
     * @return [Either.Left] of [com.wire.kalium.common.error.NetworkFailure.FeatureNotSupported] if the
     * backend API version predates transient targeted messages
     */
    suspend fun sendMLSTransientTargetedMessage(batch: MLSMessageApi.TargetedMessageBatch): Either<CoreFailure, MessageSent>
}

/** Conversation state the sender reads. */
interface SendingConversations {
    suspend fun getConversationRecipients(conversationId: ConversationId): Either<CoreFailure, List<Recipient>>
    suspend fun getRecipientById(conversationId: ConversationId, userIDList: List<UserId>): Either<StorageFailure, List<Recipient>>
    suspend fun getConversationProtocolInfo(conversationId: ConversationId): Either<StorageFailure, Conversation.ProtocolInfo>
}

/** The account runtime the sender runs in: its sync state and its crypto transactions. */
interface SendingSession {
    /** Suspends until the account has processed its pending events. */
    suspend fun waitUntilLive()

    /** Suspends until sync reaches live, or fails if it is not running or fails. */
    suspend fun waitUntilLiveOrFailure(): Either<CoreFailure, Unit>

    suspend fun <R> transaction(
        name: String,
        block: suspend (transactionContext: CryptoTransactionContext) -> Either<CoreFailure, R>
    ): Either<CoreFailure, R>
}
