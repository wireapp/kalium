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

package com.wire.kalium.messaging.sending

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.MESSAGES
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.Recipient
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.SessionEstablisher
import com.wire.kalium.logic.data.message.getType
import com.wire.kalium.util.DateTimeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Sends a stored message over Proteus or MLS, repairing recipients and retrying when the backend rejects it.
 *
 * Ports: [prepareMessage] runs content preparation before encryption; [legalHoldEnabledAfter] runs the client
 * repair and reports whether legal hold became enabled for the conversation; [verifyEpoch] and
 * [handleGroupOutOfSync] recover from MLS rejections; [broadcastRecipients] lists the team and other recipients
 * of a broadcast; [enqueueSelfDeletion] starts the timer of a sent self-deleting message.
 *
 * Crypto transactions are kept short: one is open only while preparing recipients and encrypting, or while repairing
 * crypto state after a rejection, never while a message is in transit.
 */
@Suppress("LongParameterList")
class MessageSenderImpl(
    private val messages: OutgoingMessages,
    transport: MessageTransport,
    private val conversations: SendingConversations,
    private val session: SendingSession,
    private val messageSendFailureHandler: MessageSendFailureHandler,
    sessionEstablisher: SessionEstablisher,
    messageEnvelopeCreator: MessageEnvelopeCreator,
    mlsMessageCreator: MLSMessageCreator,
    private val prepareMessage: suspend (Message.Sendable) -> Either<CoreFailure, Message.Sendable>,
    legalHoldEnabledAfter: suspend (
        conversationId: ConversationId,
        messageInstant: Instant,
        handleClientsHaveChangedFailure: suspend () -> Either<CoreFailure, Unit>
    ) -> Either<CoreFailure, Boolean>,
    verifyEpoch: suspend (CryptoTransactionContext, ConversationId) -> Either<CoreFailure, Unit>,
    handleGroupOutOfSync: suspend (
        transactionContext: CryptoTransactionContext,
        conversationId: ConversationId,
        groupId: GroupID,
        failure: NetworkFailure.MlsMessageRejectedFailure.GroupOutOfSync
    ) -> Either<CoreFailure, Unit>,
    broadcastRecipients: suspend () -> Either<CoreFailure, Pair<List<Recipient>, List<Recipient>>>,
    private val enqueueSelfDeletion: (Message, Message.ExpirationData) -> Unit,
    private val scope: CoroutineScope,
    private val pendingMessagesEnabled: Boolean = false,
    /** When false, targeted and transient MLS messages are sent as persistent application messages to the whole group. */
    targetedAndTransientMLSMessagesEnabled: Boolean = false,
    private val clock: Clock = Clock.System
) : MessageSender {

    private val logger get() = kaliumLogger.withFeatureId(MESSAGES)
    private val proteusMessageSender = ProteusMessageSender(
        messages = messages,
        transport = transport,
        conversations = conversations,
        session = session,
        messageSendFailureHandler = messageSendFailureHandler,
        sessionEstablisher = sessionEstablisher,
        messageEnvelopeCreator = messageEnvelopeCreator,
        legalHoldEnabledAfter = legalHoldEnabledAfter,
        broadcastRecipients = broadcastRecipients
    )
    private val mlsMessageSender = MLSMessageSender(
        messages = messages,
        transport = transport,
        session = session,
        mlsMessageCreator = mlsMessageCreator,
        verifyEpoch = verifyEpoch,
        handleGroupOutOfSync = handleGroupOutOfSync,
        targetedAndTransientMLSMessagesEnabled = targetedAndTransientMLSMessagesEnabled
    )

    override suspend fun sendPendingMessage(
        conversationId: ConversationId,
        messageUuid: String,
    ): Either<CoreFailure, Unit> {
        session.waitUntilLive()
        return withContext(scope.coroutineContext) {
            messages.getMessageById(conversationId, messageUuid).flatMap { message ->
                val result =
                    if (message is Message.Regular) {
                        // A recovery snapshot may have been sent by another coroutine already.
                        if (message.status == Message.Status.Pending && message.visibility == Message.Visibility.VISIBLE) {
                            sendMessage(message)
                        } else {
                            Either.Right(Unit)
                        }
                    } else {
                        Either.Left(
                            StorageFailure.Generic(IllegalArgumentException("Client cannot send server messages"))
                        )
                    }
                result
                    .onFailure {
                        // The main app owns reconnect retries; another network loss must not remove
                        // this message from its persisted pending queue.
                        if (pendingMessagesEnabled && it is NetworkFailure.NoNetworkConnection) return@onFailure
                        val type = message.content.getType()
                        logger.i("Failed to send message of type $type. Failure = $it")
                        messageSendFailureHandler.handleFailureAndUpdateMessageStatus(
                            failure = it,
                            conversationId = conversationId,
                            messageId = messageUuid,
                            messageType = type,
                            scheduleResendIfNoNetwork = false // Right now we do not allow automatic resending of failed pending messages.
                        )
                    }
            }
        }
    }

    override suspend fun cancelPendingMessage(
        conversationId: ConversationId,
        messageUuid: String
    ): Either<CoreFailure, Boolean> = session.transaction("cancelPendingMessage") {
        messages.getMessageById(conversationId, messageUuid).fold(
            { failure ->
                if (failure == StorageFailure.DataNotFound) Either.Right(true) else Either.Left(failure)
            },
            { current ->
                val isOwnUnedited = current is Message.Regular && current.isSelfMessage &&
                    current.editStatus == Message.EditStatus.NotEdited
                if (isOwnUnedited && current.status == Message.Status.Pending) {
                    messages.deleteMessage(messageUuid, conversationId).map { true }
                } else {
                    Either.Right(false)
                }
            }
        )
    }

    private suspend fun messageToSend(message: Message.Sendable): Either<CoreFailure, Message.Sendable?> {
        if (message !is Message.Regular || message.status != Message.Status.Pending) return Either.Right(message)
        return messages.getMessageById(message.conversationId, message.id).fold(
            { failure ->
                if (failure == StorageFailure.DataNotFound) Either.Right(null) else Either.Left(failure)
            },
            { current ->
                when {
                    current !is Message.Regular || current.visibility != Message.Visibility.VISIBLE ||
                        current.status != Message.Status.Pending -> Either.Right(null)
                    else -> Either.Right(
                        message.copy(
                            // Keep prepared asset metadata, but use the latest persisted text.
                            content = if (message.content is MessageContent.Text && current.content is MessageContent.Text) {
                                current.content
                            } else message.content,
                            clientCreatedAtMillis = current.clientCreatedAtMillis
                        )
                    )
                }
            }
        )
    }

    override suspend fun sendMessage(
        message: Message.Sendable,
        messageTarget: MessageTarget,
        transient: Boolean
    ): Either<CoreFailure, Unit> =
        sendMessageWithOutcome(message, messageTarget, transient).map { Unit }

    override suspend fun sendMessageWithOutcome(
        message: Message.Sendable,
        messageTarget: MessageTarget,
        transient: Boolean
    ): Either<CoreFailure, Boolean> =
        sendPreparedMessage(message, messageTarget, transient, remainingAttempts = 1)
            .onSuccess { sentMessage -> sentMessage?.let(::startSelfDeletionIfNeeded) }
            .map { it != null }
            .onFailure {
                logger.e("Failed to send message ${message::class.simpleName}. Failure = $it")
            }

    /**
     * Rechecks, prepares and sends the message. A crypto transaction is open only while encrypting, never while the
     * message is in transit. A stale MLS send retries after catch-up, then rechecks the stored state again. User
     * content goes out only while sync is live: an event batch that fails while the send waits for it leaves sync failed.
     * Waiting for live and the stale MLS retry both happen outside the transaction, then the stored state is rechecked.
     */
    private suspend fun sendPreparedMessage(
        message: Message.Sendable,
        messageTarget: MessageTarget,
        transient: Boolean,
        remainingAttempts: Int,
        waitedForLive: Boolean = false,
    ): Either<CoreFailure, Message.Sendable?> =
        (
            if (message.isUserContent() && !session.isLive())
            Either.Left(NotLive)
        else
            messageToSend(message).flatMap { current ->
                if (current == null) return@flatMap Either.Right(null)
                prepareMessage(current).flatMap { prepared ->
                    val processedMessage = if (prepared is Message.Regular) {
                        prepared.copy(
                            clientCreatedAtMillis = delayedWritingTime(prepared.clientCreatedAtMillis, clock.now())
                        )
                    } else prepared
                    attemptToSend(processedMessage, messageTarget, transient).map { serverDate ->
                        val localDate = message.date
                        val millis = DateTimeUtil.calculateMillisDifference(localDate, serverDate)
                        // If it was the "edit" message type, we need to update the id before we promote it to "sent"
                        val isEditMessage = when (message.content) {
                            is MessageContent.MultipartEdited -> {
                                messages.updateMultipartMessage(
                                    conversationId = processedMessage.conversationId,
                                    messageContent = processedMessage.content as MessageContent.MultipartEdited,
                                    newMessageId = processedMessage.id,
                                    editInstant = processedMessage.date
                                )
                                true
                            }

                            is MessageContent.TextEdited -> {
                                messages.updateTextMessage(
                                    conversationId = processedMessage.conversationId,
                                    messageContent = processedMessage.content as MessageContent.TextEdited,
                                    newMessageId = processedMessage.id,
                                    editInstant = processedMessage.date
                                )
                                true
                            }

                            else -> false
                        }
                        messages.promoteMessageToSentUpdatingServerTime(
                            conversationId = processedMessage.conversationId,
                            messageUuid = processedMessage.id,
                            // if it's edit then we don't want to change the original message creation time, it's already a server date
                            serverDate = if (!isEditMessage) serverDate else null,
                            millis = millis,
                            clientCreatedAtMillis = (processedMessage as? Message.Regular)?.clientCreatedAtMillis
                        )
                        processedMessage
                    }
                }
        }
        ).fold({ failure ->
            when {
                failure === NotLive && waitedForLive -> Either.Left(NetworkFailure.NoNetworkConnection(null))
                failure === NotLive -> session.waitUntilLiveOrStopped().flatMap {
                    sendPreparedMessage(message, messageTarget, transient, remainingAttempts, waitedForLive = true)
                }

                failure !is MLSMessageSender.StaleUntilLive -> Either.Left(failure)
                remainingAttempts <= 0 -> Either.Left(failure.cause)
                else -> session.waitUntilLiveOrFailure().flatMap {
                    sendPreparedMessage(message, messageTarget, transient, remainingAttempts - 1)
                }
            }
        }, { sent -> Either.Right(sent) })

    override suspend fun broadcastMessage(
        message: BroadcastMessage,
        target: BroadcastMessageTarget
    ): Either<CoreFailure, Unit> =
        withContext(scope.coroutineContext) {
            proteusMessageSender.broadcast(message, target, remainingAttempts = 2).map { }
        }

    private suspend fun attemptToSend(
        message: Message.Sendable,
        messageTarget: MessageTarget,
        transient: Boolean
    ): Either<CoreFailure, Instant> {
        return conversations
            .getConversationProtocolInfo(message.conversationId)
            .flatMap { protocolInfo ->
                when (protocolInfo) {
                    is Conversation.ProtocolInfo.MLS ->
                        mlsMessageSender.send(protocolInfo, message, messageTarget, transient) { retryTarget, retryTransient ->
                            attemptToSend(message, retryTarget, retryTransient)
                        }

                    is Conversation.ProtocolInfo.Proteus, is Conversation.ProtocolInfo.Mixed -> {
                        // TODO(messaging): make this thread safe (per user)
                        proteusMessageSender.send(message, messageTarget, remainingAttempts = 1)
                    }
                }
            }
    }

    private fun startSelfDeletionIfNeeded(message: Message.Sendable) {
        message.expirationData?.let { expirationData ->
            enqueueSelfDeletion(message, expirationData)
        }
    }
}

/** Opens the transaction that encrypts [message]; user content found not live inside it fails with [NotLive]. */
internal suspend fun <T> SendingSession.encryptionTransaction(
    name: String,
    message: Message.Sendable,
    block: suspend (CryptoTransactionContext) -> Either<CoreFailure, T>,
): Either<CoreFailure, T> = transaction(name) { transactionContext ->
    if (message.isUserContent() && !isLive()) Either.Left(NotLive) else block(transactionContext)
}

/**
 * What the user writes, edits or reacts never goes out from a view of the conversation that still has events to
 * process. Calls, receipts and other signaling don't wait, and neither do deletions: an expiring self-deleting
 * message sends its deletion on its own.
 */
internal fun delayedWritingTime(clientCreatedAtMillis: Long?, now: Instant): Long? =
    clientCreatedAtMillis?.takeIf { now - Instant.fromEpochMilliseconds(it) > 5.minutes }

private fun Message.Sendable.isUserContent() = this is Message.Regular || content is MessageContent.TextEdited ||
    content is MessageContent.MultipartEdited || content is MessageContent.Reaction

/**
 * User content found sync not live, before or inside the encryption transaction; the sender waits outside the
 * transaction and retries once.
 */
internal object NotLive : CoreFailure.FeatureFailure()
