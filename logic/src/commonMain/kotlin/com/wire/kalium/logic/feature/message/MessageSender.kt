package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageEnvelope
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.failure.ProteusSendMessageFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.suspending
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.logic.util.TimeParser
import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.message.MessageEntity

interface MessageSender {
    /**
     * Given a messageUuid with a conversationId to fetch from messagesDb and try
     * to send the message with related recipients
     *
     * @param conversationId
     * @param messageUuid
     */
    suspend fun trySendingOutgoingMessageById(conversationId: ConversationId, messageUuid: String): Either<CoreFailure, Unit>

    /**
     * Given a message with a conversationId to send the message to related recipients
     *
     * @param conversationId
     * @param message
     */
    suspend fun trySendingOutgoingMessage(message: Message): Either<CoreFailure, Unit>
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

    override suspend fun trySendingOutgoingMessageById(conversationId: ConversationId, messageUuid: String): Either<CoreFailure, Unit> =
        suspending {
            syncManager.waitForSlowSyncToComplete()
            messageRepository.getMessageById(conversationId, messageUuid).flatMap { message ->
                trySendingOutgoingMessage(message)
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

    override suspend fun trySendingOutgoingMessage(message: Message): Either<CoreFailure, Unit> =
        suspending {
            attemptToSend(message)
                .flatMap { messageRemoteTime ->
                    messageRepository.updateMessageDate(message.conversationId, message.id, messageRemoteTime)
                        .flatMap {
                            messageRepository.updateMessageStatus(MessageEntity.Status.SENT, message.conversationId, message.id)
                        }.flatMap {
                            // this should make sure that pending messages are ordered correctly after one of them is sent
                            messageRepository.updatePendingMessagesAddMillisToDate(
                                message.conversationId,
                                timeParser.calculateMillisDifference(message.date, messageRemoteTime)
                            )
                        }
                }
        }

    private suspend fun attemptToSend(message: Message): Either<CoreFailure, String> =
        suspending {
            conversationRepository.getConversationProtocolInfo(message.conversationId).flatMap { protocolInfo ->
                when (protocolInfo) {
                    is ConversationEntity.ProtocolInfo.MLS -> {
                        attemptToSendWithMLS(protocolInfo.groupId, message)
                    }
                    is ConversationEntity.ProtocolInfo.Proteus -> {
                        // TODO: make this thread safe (per user)
                        attemptToSendWithProteus(message)
                    }
                }
            }
        }

    private suspend fun attemptToSendWithProteus(message: Message): Either<CoreFailure, String> =
        suspending {
            conversationRepository.getConversationRecipients(message.conversationId)
                .flatMap { recipients ->
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
    ): Either<CoreFailure, String> =
        suspending {
            mlsMessageCreator.createOutgoingMLSMessage(groupId, message).flatMap { mlsMessage ->
                // TODO handle mls-stale-message
                messageRepository.sendMLSMessage(message.conversationId, mlsMessage).map {
                    message.date //TODO return actual server time from the response
                }
            }
        }

    /**
     * Attempts to send a Proteus envelope
     * Will handle the failure and retry in case of [SendMessageFailure.ClientsHaveChanged]
     */
    private suspend fun trySendingProteusEnvelope(
        envelope: MessageEnvelope,
        message: Message,
    ): Either<CoreFailure, String> = suspending {
        messageRepository.sendEnvelope(message.conversationId, envelope).coFold(
            {
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
}
