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

interface MessageSender {
    /**
     * Given a messageUuid with a conversationId to fetch from messagesDb and try
     * to send the message with related recipients
     *
     * @param conversationId
     * @param messageUuid
     */
    suspend fun trySendingOutgoingMessage(conversationId: ConversationId, messageUuid: String): Either<CoreFailure, Unit>

    /**
     * Given a message with a conversationId to send the message to related recipients
     *
     * @param conversationId
     * @param message
     */
    suspend fun getRecipientsAndAttemptSend(conversationId: ConversationId, message: Message): Either<CoreFailure, Unit>
}

class MessageSenderImpl(
    private val messageRepository: MessageRepository,
    private val conversationRepository: ConversationRepository,
    private val syncManager: SyncManager,
    private val messageSendFailureHandler: MessageSendFailureHandler,
    private val sessionEstablisher: SessionEstablisher,
    private val messageEnvelopeCreator: MessageEnvelopeCreator
) : MessageSender {

    override suspend fun trySendingOutgoingMessage(conversationId: ConversationId, messageUuid: String): Either<CoreFailure, Unit> =
        suspending {
            syncManager.waitForSlowSyncToComplete()
            messageRepository.getMessageById(conversationId, messageUuid).flatMap { message ->
                // TODO: make this thread safe (per user)
                getRecipientsAndAttemptSend(conversationId, message)
            }
        }

    override suspend fun getRecipientsAndAttemptSend(conversationId: ConversationId, message: Message): Either<CoreFailure, Unit> =
        suspending {
            conversationRepository.getConversationRecipients(conversationId)
                .flatMap { recipients ->
                    sessionEstablisher.prepareRecipientsForNewOutgoingMessage(recipients).map { recipients }
                }.flatMap { recipients ->
                    messageEnvelopeCreator.createOutgoingEnvelope(recipients, message).flatMap { envelope ->
                        trySendingEnvelopeRetryingIfPossible(conversationId, envelope, message)
                    }.flatMap {
                        messageRepository.markMessageAsSent(conversationId, message.id)
                    }
                }
        }

    private suspend fun trySendingEnvelopeRetryingIfPossible(
        conversationId: ConversationId,
        envelope: MessageEnvelope,
        messageUuid: Message,
    ): Either<CoreFailure, Unit> = suspending {
        messageRepository.sendEnvelope(conversationId, envelope).coFold(
            {
                when (it) {
                    is SendMessageFailure.Unknown -> Either.Left(it)
                    is SendMessageFailure.ClientsHaveChanged -> messageSendFailureHandler.handleClientsHaveChangedFailure(it).flatMap {
                        getRecipientsAndAttemptSend(conversationId, messageUuid)
                    }
                }
            }, {
                Either.Right(Unit)
            })
    }
}
