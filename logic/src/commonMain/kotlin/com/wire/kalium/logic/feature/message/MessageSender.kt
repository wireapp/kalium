package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.conversation.ConversationOptions
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageEnvelope
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.failure.ProteusSendMessageFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.logic.util.TimeParser
import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.message.MessageEntity

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
    suspend fun sendMessage(message: Message): Either<CoreFailure, Unit>
}

class MessageSenderImpl(
    private val messageRepository: MessageRepository,
    private val conversationRepository: ConversationRepository,
    private val syncManager: SyncManager,
    private val messageSendFailureHandler: MessageSendFailureHandler,
    private val sessionEstablisher: SessionEstablisher,
    private val messageEnvelopeCreator: MessageEnvelopeCreator,
    private val mlsMessageCreator: MLSMessageCreator,
    private val messageSendingScheduler: MessageSendingScheduler,
    private val timeParser: TimeParser
) : MessageSender {

    override suspend fun sendPendingMessage(conversationId: ConversationId, messageUuid: String): Either<CoreFailure, Unit> {
        syncManager.waitUntilLive()
        return messageRepository.getMessageById(conversationId, messageUuid).flatMap { message ->
            sendMessage(message)
        }.onFailure {
            kaliumLogger.i("Failed to send message. Failure = $it")
            if (it is NetworkFailure.NoNetworkConnection) {
                kaliumLogger.i("Scheduling message for retrying in the future.")
                messageSendingScheduler.scheduleSendingOfPendingMessages()
            } else {
                messageRepository.updateMessageStatus(MessageEntity.Status.FAILED, conversationId, messageUuid)
            }
        }
    }

    override suspend fun sendMessage(message: Message): Either<CoreFailure, Unit> = attemptToSend(message).flatMap { messageRemoteTime ->
        messageRepository.updateMessageDate(message.conversationId, message.id, messageRemoteTime)
            .map { messageRemoteTime }
    }.flatMap { messageRemoteTime ->
        messageRepository.updateMessageStatus(MessageEntity.Status.SENT, message.conversationId, message.id)
            .map { messageRemoteTime }
    }.flatMap { messageRemoteTime ->
        // this should make sure that pending messages are ordered correctly after one of them is sent
        messageRepository.updatePendingMessagesAddMillisToDate(
            message.conversationId,
            timeParser.calculateMillisDifference(message.date, messageRemoteTime)
        )
    }

    private suspend fun attemptToSend(message: Message): Either<CoreFailure, String> =
        conversationRepository.getConversationProtocolInfo(message.conversationId).flatMap { protocolInfo ->
            when (protocolInfo) {
                is ConversationEntity.ProtocolInfo.MLS -> {
                    attemptToSendWithMLS(protocolInfo.groupId, message)
                }
                is ConversationEntity.ProtocolInfo.Proteus -> {
                    // TODO(messaging): make this thread safe (per user)
                    attemptToSendWithProteus(message)
                }
            }
        }

    private suspend fun attemptToSendWithProteus(message: Message): Either<CoreFailure, String> {
        val conversationId = message.conversationId
        return conversationRepository.getConversationRecipients(conversationId).flatMap { recipients ->
            sessionEstablisher.prepareRecipientsForNewOutgoingMessage(recipients).map { recipients }
        }.flatMap { recipients ->
            messageEnvelopeCreator.createOutgoingEnvelope(recipients, message).flatMap { envelope ->
                trySendingProteusEnvelope(envelope, message)
            }
        }
    }

    private suspend fun attemptToSendWithMLS(
        groupId: String,
        message: Message
    ): Either<CoreFailure, String> = mlsMessageCreator.createOutgoingMLSMessage(groupId, message).flatMap { mlsMessage ->
        // TODO(mls): handle mls-stale-message
        messageRepository.sendMLSMessage(message.conversationId, mlsMessage).map {
            message.date //TODO(mls): return actual server time from the response
        }
    }

    /**
     * Attempts to send a Proteus envelope
     * Will handle the failure and retry in case of [ProteusSendMessageFailure].
     */
    private suspend fun trySendingProteusEnvelope(
        envelope: MessageEnvelope,
        message: Message,
    ): Either<CoreFailure, String> = messageRepository.sendEnvelope(message.conversationId, envelope).fold({
        when (it) {
            is ProteusSendMessageFailure -> messageSendFailureHandler.handleClientsHaveChangedFailure(it).flatMap {
                attemptToSend(message)
            }
            else -> Either.Left(it)
        }
    }, {
        Either.Right(it)
    })
}
