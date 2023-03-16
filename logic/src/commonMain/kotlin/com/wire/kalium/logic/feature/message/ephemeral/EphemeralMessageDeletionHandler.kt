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
import kotlinx.datetime.Clock
import kotlin.coroutines.CoroutineContext

interface EphemeralMessageDeletionHandler {

    fun startSelfDeletion(conversationId: ConversationId, messageId: String)
    fun enqueuePendingSelfDeletionMessages()
}

// TODO:Mateusz: implement failure logic (needs to be discussed with a team)
internal class EphemeralMessageDeletionHandlerImpl(
    private val messageRepository: MessageRepository,
    private val kaliumDispatcher: KaliumDispatcher = KaliumDispatcherImpl,
    userSessionCoroutineScope: CoroutineScope
) : EphemeralMessageDeletionHandler, CoroutineScope by userSessionCoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = kaliumDispatcher.default

    private val onGoingSelfDeletionMessagesMutex = Mutex()
    private val onGoingSelfDeletionMessages = mutableMapOf<Pair<ConversationId, String>, Unit>()
    override fun startSelfDeletion(conversationId: ConversationId, messageId: String) {
        launch {
            messageRepository.getMessageById(conversationId, messageId).map { message ->
                if (message is Message.Regular) enqueueSelfDeletion(message = message)
            }
        }
    }

    private suspend fun enqueueSelfDeletion(message: Message.Regular) {
        launch {
            onGoingSelfDeletionMessagesMutex.withLock {
                val isSelfDeletionOutgoing = onGoingSelfDeletionMessages[message.conversationId to message.id] != null
                if (isSelfDeletionOutgoing) return@launch

                onGoingSelfDeletionMessages[message.conversationId to message.id] = Unit
            }

            message.expirationData?.let { expirationData ->
                with(expirationData) {
                    if (selfDeletionStatus is Message.ExpirationData.SelfDeletionStatus.NotStarted) {
                        messageRepository.markSelfDeletionStartDate(
                            conversationId = message.conversationId,
                            messageUuid = message.id,
                            deletionStartDate = Clock.System.now()
                        )
                    }

                    delay(timeLeftForDeletion())
                }

                onGoingSelfDeletionMessagesMutex.withLock {
                    onGoingSelfDeletionMessages - message.conversationId to message.id
                }

                messageRepository.deleteMessage(message.id, message.conversationId)
            }
        }
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
