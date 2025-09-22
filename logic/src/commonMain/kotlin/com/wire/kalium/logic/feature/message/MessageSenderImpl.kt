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

import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.MESSAGES
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.Recipient
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.MessageId
import com.wire.kalium.logic.data.message.BroadcastMessageOption
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageEnvelope
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.MessageSent
import com.wire.kalium.logic.data.message.SessionEstablisher
import com.wire.kalium.logic.data.message.getType
import com.wire.kalium.logic.data.prekey.UsersWithoutSessions
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.failure.LegalHoldEnabledForConversationFailure
import com.wire.kalium.logic.failure.ProteusSendMessageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.common.logger.logStructuredJson
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.client.wrapInMLSContext
import com.wire.kalium.logic.data.conversation.CreateConversationParam
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldHandler
import com.wire.kalium.messaging.sending.BroadcastMessage
import com.wire.kalium.messaging.sending.BroadcastMessageTarget
import com.wire.kalium.messaging.sending.MessageSender
import com.wire.kalium.messaging.sending.MessageTarget
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isMlsStaleMessage
import com.wire.kalium.util.DateTimeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant

// TODO(modularisation): Move to :messaging:sending.
//                       It ain't gonna be easy :)
@Suppress("LongParameterList", "TooManyFunctions")
internal class MessageSenderImpl internal constructor(
    private val messageRepository: MessageRepository,
    private val conversationRepository: ConversationRepository,
    private val mlsConversationRepository: MLSConversationRepository,
    private val syncManager: SyncManager,
    private val messageSendFailureHandler: MessageSendFailureHandler,
    private val legalHoldHandler: LegalHoldHandler,
    private val sessionEstablisher: SessionEstablisher,
    private val messageEnvelopeCreator: MessageEnvelopeCreator,
    private val mlsMessageCreator: MLSMessageCreator,
    private val messageSendingInterceptor: MessageSendingInterceptor,
    private val userRepository: UserRepository,
    private val staleEpochVerifier: StaleEpochVerifier,
    private val transactionProvider: CryptoTransactionProvider,
    private val enqueueSelfDeletion: (Message, Message.ExpirationData) -> Unit,
    private val scope: CoroutineScope
) : MessageSender {

    private val logger get() = kaliumLogger.withFeatureId(MESSAGES)

    override suspend fun sendPendingMessage(conversationId: ConversationId, messageUuid: String): Either<CoreFailure, Unit> {
        syncManager.waitUntilLive()
        return withContext(scope.coroutineContext) {
            messageRepository.getMessageById(conversationId, messageUuid).flatMap { message ->
                val result =
                    if (message is Message.Regular) {
                        sendMessage(message)
                    } else {
                        Either.Left(
                            StorageFailure.Generic(IllegalArgumentException("Client cannot send server messages"))
                        )
                    }
                result
                    .onFailure {
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

    override suspend fun sendMessage(message: Message.Sendable, messageTarget: MessageTarget): Either<CoreFailure, Unit> =
        messageSendingInterceptor
            .prepareMessage(message)
            .flatMap { processedMessage ->
                transactionProvider.transaction("sendMessage") { transactionContext ->
                    attemptToSend(transactionContext, processedMessage, messageTarget).map { serverDate ->
                        val localDate = message.date
                        val millis = DateTimeUtil.calculateMillisDifference(localDate, serverDate)
                        val isEditMessage = message.content is MessageContent.TextEdited
                        // If it was the "edit" message type, we need to update the id before we promote it to "sent"
                        if (isEditMessage) {
                            messageRepository.updateTextMessage(
                                conversationId = processedMessage.conversationId,
                                messageContent = processedMessage.content as MessageContent.TextEdited,
                                newMessageId = processedMessage.id,
                                editInstant = processedMessage.date
                            )
                        }
                        messageRepository.promoteMessageToSentUpdatingServerTime(
                            conversationId = processedMessage.conversationId,
                            messageUuid = processedMessage.id,
                            // if it's edit then we don't want to change the original message creation time, it's already a server date
                            serverDate = if (!isEditMessage) serverDate else null,
                            millis = millis
                        )
                        Unit
                    }
                }.onSuccess {
                    startSelfDeletionIfNeeded(message)
                }
            }.onFailure {
                logger.e("Failed to send message ${message::class.qualifiedName}. Failure = $it")
            }

    override suspend fun broadcastMessage(
        message: BroadcastMessage,
        target: BroadcastMessageTarget
    ): Either<CoreFailure, Unit> =
        withContext(scope.coroutineContext) {
            transactionProvider.transaction("broadcastMessage") {
                attemptToBroadcastWithProteus(it, message, target, remainingAttempts = 2).map { }
            }
        }

    private suspend fun attemptToSend(
        transactionContext: CryptoTransactionContext,
        message: Message.Sendable,
        messageTarget: MessageTarget = MessageTarget.Conversation()
    ): Either<CoreFailure, Instant> {
        return conversationRepository
            .getConversationProtocolInfo(message.conversationId)
            .flatMap { protocolInfo ->
                when (protocolInfo) {
                    is Conversation.ProtocolInfo.MLS -> {
                        attemptToSendWithMLS(transactionContext, protocolInfo, message)
                    }

                    is Conversation.ProtocolInfo.Proteus, is Conversation.ProtocolInfo.Mixed -> {
                        // TODO(messaging): make this thread safe (per user)
                        attemptToSendWithProteus(transactionContext, message, messageTarget, remainingAttempts = 1)
                    }
                }
            }
    }

    private fun startSelfDeletionIfNeeded(message: Message.Sendable) {
        message.expirationData?.let { expirationData ->
            enqueueSelfDeletion(message, expirationData)
        }
    }

    private suspend fun attemptToSendWithProteus(
        transactionContext: CryptoTransactionContext,
        message: Message.Sendable,
        messageTarget: MessageTarget,
        remainingAttempts: Int
    ): Either<CoreFailure, Instant> {
        val conversationId = message.conversationId
        val target = when (messageTarget) {
            is MessageTarget.Client -> Either.Right(messageTarget.recipients)
            is MessageTarget.Conversation -> conversationRepository.getConversationRecipients(conversationId)
            is MessageTarget.Users -> conversationRepository.getRecipientById(conversationId, messageTarget.userId)
        }

        return target
            .flatMap { recipients ->
                sessionEstablisher
                    .prepareRecipientsForNewOutgoingMessage(transactionContext.proteus, recipients)
                    .flatMap { handleUsersWithNoClientsToDeliver(conversationId, message.id, it) }
                    .map { recipients to it }
            }.flatMap { (recipients, usersWithoutSessions) ->
                messageEnvelopeCreator
                    .createOutgoingEnvelope(transactionContext.proteus, recipients, message)
                    .flatMap { envelope: MessageEnvelope ->
                        val updatedMessageTarget = when (messageTarget) {
                            is MessageTarget.Client,
                            is MessageTarget.Users -> messageTarget

                            is MessageTarget.Conversation ->
                                MessageTarget.Conversation((messageTarget.usersToIgnore + usersWithoutSessions.users).toSet())
                        }
                        trySendingProteusEnvelope(transactionContext, envelope, message, updatedMessageTarget, remainingAttempts)
                    }
            }
    }

    private suspend fun handleUsersWithNoClientsToDeliver(
        conversationId: ConversationId,
        messageId: String,
        usersWithoutSessions: UsersWithoutSessions
    ): Either<CoreFailure, UsersWithoutSessions> = if (usersWithoutSessions.hasMissingSessions()) {
        messageRepository.persistNoClientsToDeliverFailure(conversationId, messageId, usersWithoutSessions.users)
            .flatMap { Either.Right(usersWithoutSessions) }
    } else {
        Either.Right(usersWithoutSessions)
    }

    private suspend fun attemptToBroadcastWithProteus(
        transactionContext: CryptoTransactionContext,
        message: BroadcastMessage,
        target: BroadcastMessageTarget,
        remainingAttempts: Int,
    ): Either<CoreFailure, Instant> {
        return userRepository.getAllRecipients().flatMap { (teamRecipients, otherRecipients) ->
            val (option, recipients) = getBroadcastParams(
                message.senderUserId,
                message.senderClientId,
                target,
                teamRecipients,
                otherRecipients
            )

            sessionEstablisher
                .prepareRecipientsForNewOutgoingMessage(transactionContext.proteus, recipients)
                .flatMap { _ ->
                    messageEnvelopeCreator
                        .createOutgoingBroadcastEnvelope(transactionContext.proteus, recipients, message)
                        .flatMap { envelope ->
                            tryBroadcastProteusEnvelope(
                                transactionContext,
                                envelope,
                                message,
                                option,
                                target,
                                remainingAttempts
                            )
                        }
                }
        }
    }

    /**
     * Attempts to send a MLS application message
     *
     * Will handle re-trying on "mls-stale-message" after we are live again or fail if we are not syncing.
     */
    private suspend fun attemptToSendWithMLS(
        transactionContext: CryptoTransactionContext,
        protocolInfo: Conversation.ProtocolInfo.MLS,
        message: Message.Sendable
    ): Either<CoreFailure, Instant> {
        return transactionContext
            .wrapInMLSContext { mlsContext ->
                mlsConversationRepository.commitPendingProposals(mlsContext, protocolInfo.groupId)
                    .flatMap { mlsMessageCreator.createOutgoingMLSMessage(mlsContext, protocolInfo.groupId, message) }
            }
            .flatMap { mlsMessage ->
                messageRepository.sendMLSMessage(mlsMessage).fold({
                    if (it is NetworkFailure.ServerMiscommunication && it.kaliumException is KaliumException.InvalidRequestError) {
                        if ((it.kaliumException as KaliumException.InvalidRequestError).isMlsStaleMessage()) {
                            logger.logStructuredJson(
                                level = KaliumLogLevel.WARN,
                                leadingMessage = "Message Send Stale",
                                jsonStringKeyValues = mapOf(
                                    "message" to message.toLogString(),
                                    "protocolInfo" to protocolInfo.toLogMap(),
                                    "protocol" to CreateConversationParam.Protocol.MLS.name,
                                    "errorInfo" to "$it"
                                )
                            )
                            return staleEpochVerifier.verifyEpoch(transactionContext, message.conversationId)
                                .flatMap {
                                    syncManager.waitUntilLiveOrFailure().flatMap {
                                        attemptToSend(transactionContext, message)
                                    }
                                }
                        }
                    }
                    Either.Left(it)
                }, { messageSent ->
                    handleMlsRecipientsDeliveryFailure(message, messageSent).flatMap {
                        Either.Right(messageSent.time)
                    }
                })
            }
            .onFailure {
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
    }

    /**
     * Attempts to send a Proteus envelope
     * Will handle the failure and retry in case of [ProteusSendMessageFailure].
     */
    private suspend fun trySendingProteusEnvelope(
        transactionContext: CryptoTransactionContext,
        envelope: MessageEnvelope,
        message: Message.Sendable,
        messageTarget: MessageTarget,
        remainingAttempts: Int
    ): Either<CoreFailure, Instant> =
        messageRepository
            .sendEnvelope(message.conversationId, envelope, messageTarget)
            .fold({
                handleProteusError(
                    transactionContext = transactionContext,
                    failure = it,
                    action = "Send",
                    messageLogString = message.toLogString(),
                    messageId = message.id,
                    messageInstant = message.date,
                    conversationId = message.conversationId,
                    remainingAttempts = remainingAttempts
                ) { remainingAttempts ->
                    attemptToSendWithProteus(transactionContext, message, messageTarget, remainingAttempts)
                }
            }, { messageSent ->
                handleRecipientsDeliveryFailure(envelope, message, messageSent).flatMap {
                    Either.Right(messageSent.time)
                }
            }).onSuccess {
                logger.logStructuredJson(
                    level = KaliumLogLevel.INFO,
                    leadingMessage = "Message Send Success",
                    jsonStringKeyValues = mapOf(
                        "message" to message.toLogString(),
                        "protocol" to CreateConversationParam.Protocol.PROTEUS.name
                    )
                )
            }

    /**
     * Attempts to send a Proteus envelope without need to provide a specific conversationId
     * Will handle the failure and retry in case of [ProteusSendMessageFailure].
     */
    private suspend fun tryBroadcastProteusEnvelope(
        transactionContext: CryptoTransactionContext,
        envelope: MessageEnvelope,
        message: BroadcastMessage,
        option: BroadcastMessageOption,
        target: BroadcastMessageTarget,
        remainingAttempts: Int
    ): Either<CoreFailure, Instant> =
        messageRepository
            .broadcastEnvelope(envelope, option)
            .fold({
                handleProteusError(
                    transactionContext,
                    it,
                    "Broadcast",
                    message.toLogString(),
                    message.id,
                    message.date,
                    null,
                    remainingAttempts = 1
                ) {
                    attemptToBroadcastWithProteus(
                        transactionContext,
                        message,
                        target,
                        remainingAttempts
                    )
                }
            }, {
                Either.Right(it)
            }).onSuccess {
                logger.logStructuredJson(
                    level = KaliumLogLevel.INFO,
                    leadingMessage = "Message Broadcast Success",
                    jsonStringKeyValues = mapOf(
                        "message" to message.toLogString(),
                        "protocol" to CreateConversationParam.Protocol.PROTEUS.name
                    )
                )
            }

    private suspend fun handleProteusError(
        transactionContext: CryptoTransactionContext,
        failure: CoreFailure,
        action: String, // Send or Broadcast
        messageLogString: String,
        messageId: MessageId,
        messageInstant: Instant,
        conversationId: ConversationId?,
        remainingAttempts: Int,
        retry: suspend (remainingAttempts: Int) -> Either<CoreFailure, Instant>
    ): Either<CoreFailure, Instant> =
        when (failure) {
            is ProteusSendMessageFailure -> {
                logger.w(
                    "Proteus $action Failure: { \"message\" : \"${messageLogString}\", \"errorInfo\" : \"${failure}\" }"
                )
                handleLegalHoldChanges(conversationId, messageInstant) {
                    messageSendFailureHandler
                        .handleClientsHaveChangedFailure(transactionContext, failure, conversationId)
                }
                    .flatMap { legalHoldEnabled ->
                        when {
                            legalHoldEnabled -> {
                                logger.w(
                                    "Legal hold enabled, no retry after Proteus $action " +
                                            "Failure: { \"message\" : \"${messageLogString}\", \"errorInfo\" : \"${failure}\" }"
                                )
                                Either.Left(LegalHoldEnabledForConversationFailure(messageId))
                            }

                            remainingAttempts > 0 -> {
                                logger.w(
                                    "Retrying (remaining attempts: $remainingAttempts) after Proteus $action " +
                                            "Failure: { \"message\" : \"${messageLogString}\", \"errorInfo\" : \"${failure}\" }"
                                )
                                retry(remainingAttempts - 1)
                            }

                            else -> {
                                logger.e(
                                    "No remaining attempts to retry after Proteus $action " +
                                            "Failure: { \"message\" : \"${messageLogString}\", \"errorInfo\" : \"${failure}\" }"
                                )
                                Either.Left(failure)
                            }
                        }
                    }
                    .onFailure {
                        val logLine = "Fatal Proteus $action Failure: { \"message\" : \"${messageLogString}\"" +
                                " , " +
                                "\"errorInfo\" : \"${it}\"}"
                        logger.e(logLine)
                    }
            }

            else -> {
                logger.e(
                    "Message $action Failure: { \"message\" : \"${messageLogString}\", \"errorInfo\" : \"${failure}\" }"
                )
                Either.Left(failure)
            }
        }

    private suspend fun handleLegalHoldChanges(
        conversationId: ConversationId?,
        messageInstant: Instant,
        handleClientsHaveChangedFailure: suspend () -> Either<CoreFailure, Unit>
    ) =
        if (conversationId == null) handleClientsHaveChangedFailure().map { false }
        else legalHoldHandler.handleMessageSendFailure(conversationId, messageInstant, handleClientsHaveChangedFailure)

    private fun getBroadcastParams(
        selfUserId: UserId,
        selfClientId: ClientId,
        target: BroadcastMessageTarget,
        teamRecipients: List<Recipient>,
        otherRecipients: List<Recipient>
    ): Pair<BroadcastMessageOption, List<Recipient>> {

        val broadcastRecipients = when (target) {
            is BroadcastMessageTarget.AllUsers -> teamRecipients + otherRecipients
            is BroadcastMessageTarget.OnlyTeam -> teamRecipients
        }
            .toSet()
            .map { recipient ->
                if (recipient.id == selfUserId) {
                    recipient.copy(clients = recipient.clients.filter { it != selfClientId })
                } else {
                    recipient
                }
            }
            .take(target.limit)

        return BroadcastMessageOption.ReportSome(broadcastRecipients.map { it.id }) to broadcastRecipients
    }

    /**
     * At this point the message was SENT, here we are mapping/persisting the recipients that couldn't get the message.
     */
    private suspend fun handleRecipientsDeliveryFailure(envelope: MessageEnvelope, message: Message, messageSent: MessageSent) =
        if (messageSent.failedToConfirmClients.isEmpty()) Either.Right(Unit)
        else {
            val usersWithoutSessions =
                messageSent.failedToConfirmClients.filter { failedIds -> failedIds !in envelope.recipients.map { it.userId } }
            if (usersWithoutSessions.isNotEmpty()) {
                messageRepository.persistNoClientsToDeliverFailure(message.conversationId, message.id, usersWithoutSessions)
            }

            val filteredUsersFailed = messageSent.failedToConfirmClients.minus(usersWithoutSessions.toSet())
            if (filteredUsersFailed.isNotEmpty()) {
                messageRepository.persistRecipientsDeliveryFailure(message.conversationId, message.id, filteredUsersFailed)
            } else {
                Either.Right(Unit)
            }
        }

    private suspend fun handleMlsRecipientsDeliveryFailure(message: Message, messageSent: MessageSent) =
        if (messageSent.failedToConfirmClients.isEmpty()) Either.Right(Unit)
        else {
            messageRepository.persistRecipientsDeliveryFailure(message.conversationId, message.id, messageSent.failedToConfirmClients)
        }
}
