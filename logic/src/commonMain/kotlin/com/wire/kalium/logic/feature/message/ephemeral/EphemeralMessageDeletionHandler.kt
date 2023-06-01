package com.wire.kalium.logic.feature.message.ephemeral

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageRepository
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

internal interface EphemeralMessageDeletionHandler {

    fun startSelfDeletion(conversationId: ConversationId, messageId: String)
    fun enqueueSelfDeletion(message: Message.Regular)
    fun enqueuePendingSelfDeletionMessages()
}

internal class EphemeralMessageDeletionHandlerImpl(
    private val messageRepository: MessageRepository,
    private val kaliumDispatcher: KaliumDispatcher = KaliumDispatcherImpl,
    private val deleteEphemeralMessageForSelfUserAsReceiver: DeleteEphemeralMessageForSelfUserAsReceiverUseCase,
    private val deleteEphemeralMessageForSelfUserAsSender: DeleteEphemeralMessageForSelfUserAsSenderUseCase,
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

    override fun enqueueSelfDeletion(message: Message.Regular) {
        val canBeDeleted = when(message.status) {
            Message.Status.PENDING -> false
            Message.Status.SENT,
            Message.Status.READ,
            Message.Status.FAILED,
            Message.Status.FAILED_REMOTELY -> true
        }
        if (!canBeDeleted) return
        launch {
            ongoingSelfDeletionMessagesMutex.withLock {
                val isSelfDeletionOutgoing = ongoingSelfDeletionMessages[message.conversationId to message.id] != null
                if (isSelfDeletionOutgoing) return@launch

                addToOutgoingDeletion(message)
            }

            markDeletionDateAndWait(message)
            deleteMessage(message)
        }
    }

    private suspend fun deleteMessage(message: Message.Regular) {
        removeFromOutgoingDeletion(message)

        if (message.isSelfMessage) {
            deleteEphemeralMessageForSelfUserAsSender(message.conversationId, message.id)
        } else {
            deleteEphemeralMessageForSelfUserAsReceiver(message.conversationId, message.id)
        }
    }

    private suspend fun removeFromOutgoingDeletion(message: Message.Regular) {
        ongoingSelfDeletionMessagesMutex.withLock {
            ongoingSelfDeletionMessages - message.conversationId to message.id
        }
    }

    private suspend fun markDeletionDateAndWait(message: Message.Regular) {
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
