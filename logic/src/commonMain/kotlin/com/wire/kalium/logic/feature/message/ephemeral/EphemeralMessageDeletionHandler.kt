package com.wire.kalium.logic.feature.message.ephemeral

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.feature.message.DeleteMessageUseCase
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.CoroutineContext

interface EphemeralMessageDeletionHandler {

    fun startSelfDeletion(conversationId: ConversationId, messageId: String)
    fun enqueuePendingSelfDeletionMessages()
}

internal class EphemeralMessageDeletionHandlerImpl(
    private val messageRepository: MessageRepository,
    private val kaliumDispatcher: KaliumDispatcher = KaliumDispatcherImpl,
    private val deleteMessageUseCase: DeleteMessageUseCase,
    userSessionCoroutineScope: CoroutineScope
) : EphemeralMessageDeletionHandler, CoroutineScope by userSessionCoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = kaliumDispatcher.default

    private val ongoingSelfDeletionMessagesMutex = Mutex()
    private val ongoingSelfDeletionMessages = mutableMapOf<Pair<ConversationId, String>, Unit>()
    override fun startSelfDeletion(conversationId: ConversationId, messageId: String) {
        launch {
            messageRepository.getMessageById(conversationId, messageId).map { message ->
                if (message is Message.Regular) enqueueSelfDeletion(message = message)
            }
        }
    }

    private suspend fun enqueueSelfDeletion(message: Message.Regular) {
        launch {
            ongoingSelfDeletionMessagesMutex.withLock {
                val isSelfDeletionOutgoing = ongoingSelfDeletionMessages[message.conversationId to message.id] != null
                if (isSelfDeletionOutgoing) return@launch

                addToOutgoingDeletion(message)
            }

            markAndWaitToDelete(message)
            deleteMessage(message)
        }
    }

    /**
     * in case we are enqueue the message send by our self, we only mark it as
     * [com.wire.kalium.persistence.dao.message.MessageEntity.Visibility.DELETED]
     * and we relay on the receiving side to inform us about the
     * moment we are ready to delete it completely, that is done by
     * invoking [DeleteMessageUseCase], which the receiver will do at some point
     * once [startSelfDeletion] is invoked on his side and this piece of logic will be
     * reached.
     **/
    private suspend fun deleteMessage(message: Message.Regular) {
        removeFromOutgoingDeletion(message)

        if (message.isSelfMessage) {
            messageRepository.markMessageAsDeleted(message.id, message.conversationId)
        } else {
//             deleteMessageUseCase(message.conversationId, message.id, true)
        }
    }

    private suspend fun removeFromOutgoingDeletion(message: Message.Regular) {
        ongoingSelfDeletionMessagesMutex.withLock {
            ongoingSelfDeletionMessages - message.conversationId to message.id
        }
    }

    private suspend fun markAndWaitToDelete(message: Message.Regular) {
        message.expirationData?.let { expirationData ->
            with(expirationData) {
                if (selfDeletionStatus is Message.ExpirationData.SelfDeletionStatus.NotStarted) {
                    messageRepository.markSelfDeletionStartDate(
                        conversationId = message.conversationId,
                        messageUuid = message.id,
                        deletionStartDate = kotlinx.datetime.Clock.System.now()
                    )
                }

                delay(timeLeftForDeletion())
            }
        }
    }

    private fun addToOutgoingDeletion(message: Message.Regular) {
        ongoingSelfDeletionMessages[message.conversationId to message.id] = Unit
    }

    override fun enqueuePendingSelfDeletionMessages() {
        launch {
            messageRepository.getEphemeralMessagesMarkedForDeletion()
                .onSuccess { ephemeralMessages ->
                    ephemeralMessages.forEach { ephemeralMessage ->
                        if (ephemeralMessage is Message.Regular) enqueueSelfDeletion(ephemeralMessage)
                    }
                }
        }
    }
}
