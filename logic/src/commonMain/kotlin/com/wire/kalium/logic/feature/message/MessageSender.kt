/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.MESSAGES
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationOptions
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageEnvelope
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.MessageSent
import com.wire.kalium.logic.failure.ProteusSendMessageFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isMlsStaleMessage
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.util.DateTimeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import kotlinx.datetime.toInstant

/**
 * Responsible for orchestrating all the pieces necessary
 * for sending a message to the wanted recipients.
 * Will handle reading and updating message status, retries
 * in case of connectivity issues, and encryption based on
 * [ConversationOptions.Protocol].
 *
 * @see MessageSenderImpl
 */
interface MessageSender {
    /**
     * Given the [ConversationId] and UUID of a message that
     * was previously persisted locally,
     * attempts to send the message to suitable recipients.
     *
     * Will handle all the needed encryption and possible set-up
     * steps and retries depending on the [ConversationOptions.Protocol].
     *
     * In case of connectivity failure, will schedule a retry in the future using a [MessageSendingScheduler].
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
    suspend fun sendMessage(message: Message.Sendable, messageTarget: MessageTarget = MessageTarget.Conversation): Either<CoreFailure, Unit>

    /**
     * Attempts to send the given Client Discovery [Message] to suitable recipients.
     */
    suspend fun sendClientDiscoveryMessage(message: Message.Regular): Either<CoreFailure, String>
}

@Suppress("LongParameterList")
internal class MessageSenderImpl internal constructor(
    private val messageRepository: MessageRepository,
    private val conversationRepository: ConversationRepository,
    private val mlsConversationRepository: MLSConversationRepository,
    private val syncManager: SyncManager,
    private val messageSendFailureHandler: MessageSendFailureHandler,
    private val sessionEstablisher: SessionEstablisher,
    private val messageEnvelopeCreator: MessageEnvelopeCreator,
    private val mlsMessageCreator: MLSMessageCreator,
    private val messageSendingScheduler: MessageSendingScheduler,
    private val messageSendingInterceptor: MessageSendingInterceptor,
    private val scope: CoroutineScope
) : MessageSender {

    private val logger get() = kaliumLogger.withFeatureId(MESSAGES)

    override suspend fun sendPendingMessage(conversationId: ConversationId, messageUuid: String): Either<CoreFailure, Unit> {
        syncManager.waitUntilLive()
        return withContext(scope.coroutineContext) {
            messageRepository.getMessageById(conversationId, messageUuid).flatMap { message ->
                if (message is Message.Regular) sendMessage(message)
                else Either.Left(StorageFailure.Generic(IllegalArgumentException("Client cannot send server messages")))
            }.onFailure {
                logger.i("Failed to send message. Failure = $it")
                when (it) {
                    is NetworkFailure.FederatedBackendFailure -> {
                        logger.i("Failed due to federation context availability.")
                        messageRepository.updateMessageStatus(MessageEntity.Status.FAILED_REMOTELY, conversationId, messageUuid)
                    }

                    is NetworkFailure.NoNetworkConnection -> {
                        logger.i("Scheduling message for retrying in the future.")
                        messageSendingScheduler.scheduleSendingOfPendingMessages()
                    }

                    else -> {
                        messageRepository.updateMessageStatus(MessageEntity.Status.FAILED, conversationId, messageUuid)
                    }
                }
            }
        }
    }

    override suspend fun sendMessage(message: Message.Sendable, messageTarget: MessageTarget): Either<CoreFailure, Unit> =
        messageSendingInterceptor.prepareMessage(message).flatMap { processedMessage ->
            attemptToSend(processedMessage, messageTarget).map { messageRemoteTime ->
                val serverDate = messageRemoteTime.toInstant()
                val localDate = message.date.toInstant()
                val millis = DateTimeUtil.calculateMillisDifference(localDate, serverDate)
                messageRepository.promoteMessageToSentUpdatingServerTime(
                    processedMessage.conversationId,
                    processedMessage.id,
                    serverDate,
                    millis
                )
                Unit
            }
        }

    override suspend fun sendClientDiscoveryMessage(message: Message.Regular): Either<CoreFailure, String> = attemptToSend(message)

    private suspend fun attemptToSend(
        message: Message.Sendable,
        messageTarget: MessageTarget = MessageTarget.Conversation
    ): Either<CoreFailure, String> {
        return conversationRepository.getConversationProtocolInfo(message.conversationId).flatMap { protocolInfo ->
            when (protocolInfo) {
                is Conversation.ProtocolInfo.MLS -> {
                    attemptToSendWithMLS(protocolInfo.groupId, message)
                }

                is Conversation.ProtocolInfo.Proteus -> {
                    // TODO(messaging): make this thread safe (per user)
                    attemptToSendWithProteus(message, messageTarget)
                }
            }
        }
    }

    private suspend fun attemptToSendWithProteus(
        message: Message.Sendable,
        messageTarget: MessageTarget
    ): Either<CoreFailure, String> {
        val conversationId = message.conversationId
        val target = when (messageTarget) {
            is MessageTarget.Client -> Either.Right(messageTarget.recipients)
            is MessageTarget.Conversation -> conversationRepository.getConversationRecipients(conversationId)
        }

        return target.flatMap { recipients ->
            sessionEstablisher.prepareRecipientsForNewOutgoingMessage(recipients).map { recipients }
            // map a filtered recipients in case there is an error for x,y,z clients of users
        }.fold({
            // if (it is NetworkFailure.FederatedBackendError)
            // handle federated failure to filter clients and add to QualifiedMessageOption.IgnoreSome
            Either.Left(it)
        }, { recipients ->
            messageEnvelopeCreator.createOutgoingEnvelope(recipients, message).flatMap { envelope ->
                trySendingProteusEnvelope(envelope, message, messageTarget)
            }
        })
    }

    /**
     * Attempts to send a MLS application message
     *
     * Will handle re-trying on "mls-stale-message" after we are live again or fail if we are not syncing.
     */
    private suspend fun attemptToSendWithMLS(groupId: GroupID, message: Message.Sendable): Either<CoreFailure, String> =
        mlsConversationRepository.commitPendingProposals(groupId).flatMap {
            mlsMessageCreator.createOutgoingMLSMessage(groupId, message).flatMap { mlsMessage ->
                messageRepository.sendMLSMessage(message.conversationId, mlsMessage).fold({
                    if (it is NetworkFailure.ServerMiscommunication && it.kaliumException is KaliumException.InvalidRequestError) {
                        if (it.kaliumException.isMlsStaleMessage()) {
                            logger.w("Encrypted MLS message for outdated epoch '${message.id}', re-trying..")
                            return syncManager.waitUntilLiveOrFailure().flatMap {
                                attemptToSend(message)
                            }
                        }
                    }
                    Either.Left(it)
                }, {
                    Either.Right(it)
                })
            }
        }

    /**
     * Attempts to send a Proteus envelope
     * Will handle the failure and retry in case of [ProteusSendMessageFailure].
     */
    private suspend fun trySendingProteusEnvelope(
        envelope: MessageEnvelope,
        message: Message.Sendable,
        messageTarget: MessageTarget
    ): Either<CoreFailure, String> =
        messageRepository.sendEnvelope(message.conversationId, envelope, messageTarget).fold({
            when (it) {
                is ProteusSendMessageFailure -> messageSendFailureHandler.handleClientsHaveChangedFailure(it).flatMap {
                    attemptToSendWithProteus(message, messageTarget)
                }

                else -> Either.Left(it)
            }
        }, { messageSent ->
            handleRecipientsDeliveryFailure(message, messageSent).flatMap {
                Either.Right(messageSent.time)
            }
        })

    /**
     * At this point the message was SENT, here we are mapping/persisting the recipients that couldn't get the message.
     */
    private suspend fun handleRecipientsDeliveryFailure(message: Message, messageSent: MessageSent) =
        if (messageSent.failed.isEmpty()) {
            Either.Right(Unit)
        } else {
            messageRepository.persistRecipientsDeliveryFailure(message.conversationId, message.id, messageSent)
        }

}
