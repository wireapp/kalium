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
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.error.wrapNetworkMlsFailureIfApplicable
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.flatMapLeft
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.common.logger.logStructuredJson
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.MESSAGES
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.CreateConversationParam
import com.wire.kalium.logic.data.conversation.Recipient
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageSent
import com.wire.kalium.logic.data.message.SessionEstablisher
import com.wire.kalium.logic.data.message.getType
import com.wire.kalium.network.api.base.authenticated.message.MLSMessageApi
import com.wire.kalium.util.DateTimeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
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
@Suppress("LongParameterList", "TooManyFunctions")
class MessageSenderImpl(
    private val messages: OutgoingMessages,
    private val transport: MessageTransport,
    private val conversations: SendingConversations,
    private val session: SendingSession,
    private val messageSendFailureHandler: MessageSendFailureHandler,
    sessionEstablisher: SessionEstablisher,
    messageEnvelopeCreator: MessageEnvelopeCreator,
    private val mlsMessageCreator: MLSMessageCreator,
    private val prepareMessage: suspend (Message.Sendable) -> Either<CoreFailure, Message.Sendable>,
    legalHoldEnabledAfter: suspend (
        conversationId: ConversationId,
        messageInstant: Instant,
        handleClientsHaveChangedFailure: suspend () -> Either<CoreFailure, Unit>
    ) -> Either<CoreFailure, Boolean>,
    private val verifyEpoch: suspend (CryptoTransactionContext, ConversationId) -> Either<CoreFailure, Unit>,
    private val handleGroupOutOfSync: suspend (
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
    private val targetedAndTransientMLSMessagesEnabled: Boolean = false
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
                    // Keep prepared asset metadata and other caller-specific fields, but do not send stale text.
                    message.content is MessageContent.Text && current.content is MessageContent.Text ->
                        Either.Right(message.copy(content = current.content))
                    else -> Either.Right(message)
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
                prepareMessage(current).flatMap { processedMessage ->
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
                            millis = millis
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

                failure !is StaleUntilLive -> Either.Left(failure)
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
        messageTarget: MessageTarget = MessageTarget.Conversation(),
        transient: Boolean = false
    ): Either<CoreFailure, Instant> {
        return conversations
            .getConversationProtocolInfo(message.conversationId)
            .flatMap { protocolInfo ->
                when (protocolInfo) {
                    is Conversation.ProtocolInfo.MLS -> {
                        if (
                            message.content is MessageContent.Calling &&
                            protocolInfo.groupState == Conversation.ProtocolInfo.MLSCapable.GroupState.PENDING_AFTER_RESET
                        ) {
                            logger.i("Skipping calling message while MLS reset is pending")
                            Either.Right(message.date)
                        } else {
                            attemptToSendWithMLS(protocolInfo, message, messageTarget, transient)
                        }
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

    /**
     * Attempts to send a MLS application message, or a targeted message when [messageTarget] addresses specific
     * users or clients. Either one is transient when [transient] is true.
     *
     * Recovering from rejections in [handleMlsRejection].
     */
    private suspend fun attemptToSendWithMLS(
        protocolInfo: Conversation.ProtocolInfo.MLS,
        message: Message.Sendable,
        messageTarget: MessageTarget,
        transient: Boolean
    ): Either<CoreFailure, Instant> =
        session.encryptionTransaction("encryptMlsMessage", message) { transactionContext ->
            createOutgoingMLSMessage(transactionContext, protocolInfo.groupId, message, messageTarget, transient)
        }
            .flatMap { sendMLSMessage ->
                sendMLSMessage().fold({
                    handleTargetedTransientMessagesNotSupported(it, message)
                        .flatMapLeft {
                            handleMlsRejection(it, protocolInfo, message, messageTarget, transient)
                        }
                }, { messageSent ->
                    handleMlsRecipientsDeliveryFailure(message, messageSent).flatMap {
                        Either.Right(messageSent.time)
                    }
                })
            }
            .onFailure {
                if (it is StaleUntilLive || it === NotLive) return@onFailure
                logger.logStructuredJson(
                    level = KaliumLogLevel.ERROR,
                    leadingMessage = "Message Send Failure",
                    jsonStringKeyValues = mapOf(
                        "message" to message.toLogString(),
                        "protocolInfo" to protocolInfo.toLogMap(),
                        "protocol" to CreateConversationParam.Protocol.MLS.name,
                        "errorInfo" to "$it"
                    )
                )
            }.onSuccess {
                logger.logStructuredJson(
                    level = KaliumLogLevel.INFO,
                    leadingMessage = "Message Send Success",
                    jsonStringKeyValues = mapOf(
                        "message" to message.toLogString(),
                        "protocolInfo" to protocolInfo.toLogMap(),
                        "protocol" to CreateConversationParam.Protocol.MLS.name,
                    )
                )
            }

    private suspend fun handleTargetedTransientMessagesNotSupported(
        failure: CoreFailure,
        message: Message.Sendable
    ): Either<CoreFailure, Instant> =
        if (failure is NetworkFailure.FeatureNotSupported) {
            logger.w(
                "Targeted & transient MLS messages is not supported by the backend, " +
                        "sending a persistent message to the whole group"
            )
            attemptToSend(message)
        } else {
            Either.Left(failure)
        }

    /**
     * Recovers from a rejected MLS send. On "mls-stale-message" it verifies the epoch and returns [StaleUntilLive],
     * so [sendPreparedMessage] can retry once we are live again.
     */
    private suspend fun handleMlsRejection(
        failure: CoreFailure,
        protocolInfo: Conversation.ProtocolInfo.MLS,
        message: Message.Sendable,
        messageTarget: MessageTarget,
        transient: Boolean
    ): Either<CoreFailure, Instant> =
        when (val mlsRejectionCause = (failure.wrapNetworkMlsFailureIfApplicable() as? MLSFailure.MessageRejected)?.cause) {
            is NetworkFailure.MlsMessageRejectedFailure.GroupOutOfSync -> {
                session.transaction("handleGroupOutOfSync") { transactionContext ->
                    handleGroupOutOfSync(
                        transactionContext,
                        message.conversationId,
                        protocolInfo.groupId,
                        mlsRejectionCause
                    )
                }.flatMap { attemptToSend(message, messageTarget, transient) }
            }
            is NetworkFailure.MlsMessageRejectedFailure.StaleMessage -> {
                logger.logStructuredJson(
                    level = KaliumLogLevel.WARN,
                    leadingMessage = "Message Send Stale",
                    jsonStringKeyValues = mapOf(
                        "message" to message.toLogString(),
                        "protocolInfo" to protocolInfo.toLogMap(),
                        "protocol" to CreateConversationParam.Protocol.MLS.name,
                        "errorInfo" to "$failure"
                    )
                )
                val stale = StaleUntilLive(failure)
                session.transaction("verifyEpoch") { transactionContext ->
                    verifyEpoch(transactionContext, message.conversationId)
                }.flatMap { Either.Left(stale) }
            }
            else -> Either.Left(failure)
        }

    /**
     * Encrypts [message] for [messageTarget] and returns the request that sends it.
     * A targeted message that selects no group member is not sent. A transient or targeted message is sent as a
     * persistent application message to the whole group instead when [targetedAndTransientMLSMessagesEnabled] is
     * false or the backend doesn't support it.
     */
    private suspend fun createOutgoingMLSMessage(
        transactionContext: CryptoTransactionContext,
        groupId: GroupID,
        message: Message.Sendable,
        messageTarget: MessageTarget,
        transient: Boolean
    ): Either<CoreFailure, suspend () -> Either<CoreFailure, MessageSent>> {
        val isTargeted = targetedAndTransientMLSMessagesEnabled &&
            (messageTarget !is MessageTarget.Conversation || messageTarget.usersToIgnore.isNotEmpty())
        val isTransient = targetedAndTransientMLSMessagesEnabled && transient
        val outgoingMessage = when {
            !isTargeted && !isTransient ->
                return mlsMessageCreator.prepareMLSGroupAndCreateOutgoingMLSMessage(transactionContext, groupId, message)
                    .map { mlsMessage -> { transport.sendMLSMessage(mlsMessage) } }

            !isTargeted ->
                mlsMessageCreator.prepareMLSGroupAndCreateOutgoingTransientMLSMessage(transactionContext, groupId, message)
                    .map { mlsMessage -> suspend { transport.sendMLSTransientMessage(mlsMessage) } }

            else ->
                mlsMessageCreator.prepareMLSGroupAndCreateOutgoingTargetedMLSMessage(
                    transactionContext,
                    groupId,
                    message,
                    messageTarget,
                    isTransient
                ).map { batch -> sendTargetedBatch(batch, isTransient, message) }
        }
        return outgoingMessage
    }

    private fun sendTargetedBatch(
        batch: MLSMessageApi.TargetedMessageBatch,
        transient: Boolean,
        message: Message.Sendable
    ): suspend () -> Either<CoreFailure, MessageSent> = {
        when {
            batch.value.isEmpty() -> Either.Right(MessageSent(time = message.date))
            transient -> transport.sendMLSTransientTargetedMessage(batch)
            else -> transport.sendMLSTargetedMessage(batch)
        }
    }

    private suspend fun handleMlsRecipientsDeliveryFailure(message: Message, messageSent: MessageSent) =
        if (messageSent.failedToConfirmClients.isEmpty()) Either.Right(Unit)
        else {
            messages.persistRecipientsDeliveryFailure(message.conversationId, message.id, messageSent.failedToConfirmClients)
        }

    /** A stale MLS send whose epoch was verified. It never leaves the sender: [sendPreparedMessage] retries it or returns [cause]. */
    private class StaleUntilLive(val cause: CoreFailure) : CoreFailure.FeatureFailure()
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
private fun Message.Sendable.isUserContent() = this is Message.Regular || content is MessageContent.TextEdited ||
    content is MessageContent.MultipartEdited || content is MessageContent.Reaction

/**
 * User content found sync not live, before or inside the encryption transaction; the sender waits outside the
 * transaction and retries once.
 */
private object NotLive : CoreFailure.FeatureFailure()
