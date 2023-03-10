package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlin.coroutines.CoroutineContext


interface EphemeralMessageDeletionHandler {

    fun startSelfDeletion(conversationId: ConversationId, messageId: String)
    fun enqueuePendingSelfDeletionMessages()
}


// TODO:Mateusz: implement failure logic
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
            onGoingSelfDeletionMessagesMutex.withLock {
                val isSelfDeletionOutgoing = onGoingSelfDeletionMessages[conversationId to messageId] != null
                if (isSelfDeletionOutgoing) return@launch

                onGoingSelfDeletionMessages[conversationId to messageId] = Unit
            }

            messageRepository.getMessageById(conversationId, messageId).map { message ->
                if (message is Message.Regular) enqueueSelfDeletion(message = message)
            }
        }
    }

    private suspend fun enqueueSelfDeletion(message: Message.Regular) {
        if (message.isEphemeralMessage) {
            val expirationData = message.expirationData!!

            with(expirationData) {
                if (!isDeletionStartedInThePast()) {
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

    override fun enqueuePendingSelfDeletionMessages() {
        launch {
            messageRepository.getEphemeralMessages()
                .onSuccess { ephemeralMessages ->
                    ephemeralMessages.forEach { ephemeralMessage ->
                        launch {
                            enqueueSelfDeletion(message = ephemeralMessage)
                        }
                    }
                }
        }
    }
}
