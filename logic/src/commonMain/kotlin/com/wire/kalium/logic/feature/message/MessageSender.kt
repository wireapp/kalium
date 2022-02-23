package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationId
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.suspending
import com.wire.kalium.logic.sync.SyncManager

interface MessageSender {
    suspend fun trySendingOutgoingMessage(conversationId: ConversationId, messageUuid: String): Either<CoreFailure, Unit>
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

            // TODO: make this thread safe (per user)
            conversationRepository.getConversationRecipients(conversationId)
                .flatMap { recipients ->
                    sessionEstablisher.prepareRecipientsForNewOutgoingMessage(recipients).map { recipients }
                }.flatMap { recipients ->
                    messageRepository.getMessageById(conversationId, messageUuid).flatMap { message ->
                        messageEnvelopeCreator.createOutgoingEnvelope(recipients, message).flatMap { envelope ->
                            messageRepository.sendEnvelope(conversationId, envelope)
                            //TODO("Handle failures too")
                        }.flatMap {
                            TODO("Mark message as sent")
                        }
                    }
                }
        }
}
