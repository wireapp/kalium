package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageEnvelope
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.failure.SendMessageFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.suspending
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.persistence.dao.ConversationEntity
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.toInstant
import kotlinx.datetime.until

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
    suspend fun trySendingOutgoingMessage(conversationId: ConversationId, message: Message): Either<CoreFailure, Unit>
}

class MessageSenderImpl(
    private val messageRepository: MessageRepository,
    private val conversationRepository: ConversationRepository,
    private val syncManager: SyncManager,
    private val messageSendFailureHandler: MessageSendFailureHandler,
    private val sessionEstablisher: SessionEstablisher,
    private val messageEnvelopeCreator: MessageEnvelopeCreator,
    private val mlsMessageCreator: MLSMessageCreator
) : MessageSender {

    override suspend fun trySendingOutgoingMessageById(conversationId: ConversationId, messageUuid: String): Either<CoreFailure, Unit> =
        suspending {
            syncManager.waitForSlowSyncToComplete()
            messageRepository.getMessageById(conversationId, messageUuid).flatMap { message ->
                trySendingOutgoingMessage(conversationId, message)
            }
        }

    override suspend fun trySendingOutgoingMessage(conversationId: ConversationId, message: Message): Either<CoreFailure, Unit> =
        suspending {
            attemptToSend(conversationId, message)
                .flatMap { messageRemoteTime ->
                    messageRepository.updateMessageDate(conversationId, message.id, messageRemoteTime)
                    messageRepository.markMessageAsSent(conversationId, message.id)
                    val millisDiff = message.date.toInstant().until(messageRemoteTime.toInstant(), DateTimeUnit.MILLISECOND)
                    // this should make sure that pending messages are ordered correctly after one of them is sent
                    messageRepository.updatePendingMessagesAddMillisToDate(conversationId, millisDiff)
                }
        }

    private suspend fun attemptToSend(conversationId: ConversationId, message: Message): Either<CoreFailure, String> =
        suspending {
            conversationRepository.getConversationProtocolInfo(conversationId).flatMap { protocolInfo ->
                when (protocolInfo) {
                    is ConversationEntity.ProtocolInfo.MLS -> {
                        attemptToSendWithMLS(conversationId, protocolInfo.groupId, message)
                    }
                    is ConversationEntity.ProtocolInfo.Proteus -> {
                        // TODO: make this thread safe (per user)
                        attemptToSendWithProteus(conversationId, message)
                    }
                }
            }
        }

    private suspend fun attemptToSendWithProteus(conversationId: ConversationId, message: Message): Either<CoreFailure, String> =
        suspending {
            conversationRepository.getConversationRecipients(conversationId)
                .flatMap { recipients ->
                    sessionEstablisher.prepareRecipientsForNewOutgoingMessage(recipients).map { recipients }
                }.flatMap { recipients ->
                    messageEnvelopeCreator.createOutgoingEnvelope(recipients, message).flatMap { envelope ->
                        trySendingEnvelopeRetryingIfPossible(conversationId, envelope, message)
                    }
                }
        }

    private suspend fun attemptToSendWithMLS(
        conversationId: ConversationId,
        groupId: String,
        message: Message
    ): Either<CoreFailure, String> = suspending {
        mlsMessageCreator.createOutgoingMLSMessage(groupId, message).flatMap { mlsMessage ->
            // TODO handle mls-stale-message
            messageRepository.sendMLSMessage(conversationId, mlsMessage).map {
                message.date //TODO return actual server time from the response
            }
        }
    }

    private suspend fun trySendingEnvelopeRetryingIfPossible(
        conversationId: ConversationId,
        envelope: MessageEnvelope,
        messageUuid: Message,
    ): Either<CoreFailure, String> = suspending {
        messageRepository.sendEnvelope(conversationId, envelope).coFold(
            {
                when (it) {
                    is SendMessageFailure.Unknown -> Either.Left(it)
                    is SendMessageFailure.ClientsHaveChanged -> messageSendFailureHandler.handleClientsHaveChangedFailure(it).flatMap {
                        attemptToSend(conversationId, messageUuid)
                    }
                }
            }, {
                Either.Right(it)
            })
    }
}
