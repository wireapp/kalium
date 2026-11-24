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
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.common.logger.logStructuredJson
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.MESSAGES
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.CreateConversationParam
import com.wire.kalium.logic.data.conversation.Recipient
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.MessageId
import com.wire.kalium.logic.data.message.BroadcastMessageOption
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageEnvelope
import com.wire.kalium.logic.data.message.MessageSent
import com.wire.kalium.logic.data.message.SessionEstablisher
import com.wire.kalium.logic.data.prekey.UsersWithoutSessions
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.failure.LegalHoldEnabledForConversationFailure
import com.wire.kalium.logic.failure.ProteusSendMessageFailure
import kotlin.time.Instant

@Suppress("LongParameterList")
internal class ProteusMessageSender(
    private val messages: OutgoingMessages,
    private val transport: MessageTransport,
    private val conversations: SendingConversations,
    private val session: SendingSession,
    private val messageSendFailureHandler: MessageSendFailureHandler,
    private val sessionEstablisher: SessionEstablisher,
    private val messageEnvelopeCreator: MessageEnvelopeCreator,
    private val legalHoldEnabledAfter: suspend (
        conversationId: ConversationId,
        messageInstant: Instant,
        handleClientsHaveChangedFailure: suspend () -> Either<CoreFailure, Unit>
    ) -> Either<CoreFailure, Boolean>,
    private val broadcastRecipients: suspend () -> Either<CoreFailure, Pair<List<Recipient>, List<Recipient>>>
) {

    private val logger get() = kaliumLogger.withFeatureId(MESSAGES)

    suspend fun send(
        message: Message.Sendable,
        messageTarget: MessageTarget,
        remainingAttempts: Int
    ): Either<CoreFailure, Instant> {
        val conversationId = message.conversationId
        val target = when (messageTarget) {
            is MessageTarget.Client -> Either.Right(messageTarget.recipients)
            is MessageTarget.Conversation -> conversations.getConversationRecipients(conversationId)
            is MessageTarget.Users -> conversations.getRecipientById(conversationId, messageTarget.userId)
        }

        return target
            .flatMap { recipients ->
                session.encryptionTransaction("encryptProteusMessage", message) { transactionContext ->
                    sessionEstablisher
                        .prepareRecipientsForNewOutgoingMessage(transactionContext.proteus, recipients)
                        .flatMap { handleUsersWithNoClientsToDeliver(conversationId, message.id, it) }
                        .flatMap { usersWithoutSessions ->
                            messageEnvelopeCreator
                                .createOutgoingEnvelope(transactionContext.proteus, recipients, message)
                                .map { envelope -> envelope to usersWithoutSessions }
                        }
                }
            }.flatMap { (envelope: MessageEnvelope, usersWithoutSessions) ->
                val updatedMessageTarget = when (messageTarget) {
                    is MessageTarget.Client,
                    is MessageTarget.Users -> messageTarget

                    is MessageTarget.Conversation ->
                        MessageTarget.Conversation((messageTarget.usersToIgnore + usersWithoutSessions.users).toSet())
                }
                trySendingProteusEnvelope(envelope, message, updatedMessageTarget, remainingAttempts)
            }
    }

    private suspend fun handleUsersWithNoClientsToDeliver(
        conversationId: ConversationId,
        messageId: String,
        usersWithoutSessions: UsersWithoutSessions
    ): Either<CoreFailure, UsersWithoutSessions> = if (usersWithoutSessions.hasMissingSessions()) {
        messages.persistNoClientsToDeliverFailure(conversationId, messageId, usersWithoutSessions.users)
            .flatMap { Either.Right(usersWithoutSessions) }
    } else {
        Either.Right(usersWithoutSessions)
    }

    suspend fun broadcast(
        message: BroadcastMessage,
        target: BroadcastMessageTarget,
        remainingAttempts: Int,
    ): Either<CoreFailure, Instant> {
        return broadcastRecipients().flatMap { (teamRecipients, otherRecipients) ->
            val (option, recipients) = getBroadcastParams(
                message.senderUserId,
                message.senderClientId,
                target,
                teamRecipients,
                otherRecipients
            )

            session.transaction("encryptBroadcastMessage") { transactionContext ->
                sessionEstablisher
                    .prepareRecipientsForNewOutgoingMessage(transactionContext.proteus, recipients)
                    .flatMap { _ ->
                        messageEnvelopeCreator.createOutgoingBroadcastEnvelope(transactionContext.proteus, recipients, message)
                    }
            }.flatMap { envelope ->
                tryBroadcastProteusEnvelope(envelope, message, option, target, remainingAttempts)
            }
        }
    }

    /**
     * Attempts to send a Proteus envelope
     * Will handle the failure and retry in case of [ProteusSendMessageFailure].
     */
    private suspend fun trySendingProteusEnvelope(
        envelope: MessageEnvelope,
        message: Message.Sendable,
        messageTarget: MessageTarget,
        remainingAttempts: Int
    ): Either<CoreFailure, Instant> =
        transport
            .sendEnvelope(message.conversationId, envelope, messageTarget)
            .fold({
                handleProteusError(
                    failure = it,
                    action = "Send",
                    messageLogString = message.toLogString(),
                    messageId = message.id,
                    messageInstant = message.date,
                    conversationId = message.conversationId,
                    remainingAttempts = remainingAttempts
                ) { remainingAttempts ->
                    send(message, messageTarget, remainingAttempts)
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
        envelope: MessageEnvelope,
        message: BroadcastMessage,
        option: BroadcastMessageOption,
        target: BroadcastMessageTarget,
        remainingAttempts: Int
    ): Either<CoreFailure, Instant> =
        transport
            .broadcastEnvelope(envelope, option)
            .fold({
                handleProteusError(
                    it,
                    "Broadcast",
                    message.toLogString(),
                    message.id,
                    message.date,
                    null,
                    remainingAttempts = 1
                ) {
                    broadcast(
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
                    session.transaction("handleClientsHaveChanged") { transactionContext ->
                        messageSendFailureHandler
                            .handleClientsHaveChangedFailure(transactionContext, failure, conversationId)
                    }
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
        else legalHoldEnabledAfter(conversationId, messageInstant, handleClientsHaveChangedFailure)

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
                messages.persistNoClientsToDeliverFailure(message.conversationId, message.id, usersWithoutSessions)
            }

            val filteredUsersFailed = messageSent.failedToConfirmClients.minus(usersWithoutSessions.toSet())
            if (filteredUsersFailed.isNotEmpty()) {
                messages.persistRecipientsDeliveryFailure(message.conversationId, message.id, filteredUsersFailed)
            } else {
                Either.Right(Unit)
            }
        }
}
