package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext


interface SelfDeletingMessageManager {
    fun startSelfDeletion(conversationId: ConversationId, messageId: String)

    fun observePendingMessageDeletionState(): Flow<Map<Pair<ConversationId, String>, Long>>

    fun enqueuePendingSelfDeletionMessages()
}

internal class SelfDeletingMessageManagerImpl(
    private val userSessionCoroutineScope: CoroutineScope,
    private val kaliumDispatcher: KaliumDispatcher = KaliumDispatcherImpl,
    private val messageRepository: MessageRepository
) : SelfDeletingMessageManager, CoroutineScope by userSessionCoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = kaliumDispatcher.default.limitedParallelism(1) + SupervisorJob()

    private val outgoingTimerSelfDeletingMessagesState: MutableStateFlow<Map<Pair<ConversationId, String>, Long>> =
        MutableStateFlow(emptyMap())

    override fun startSelfDeletion(conversationId: ConversationId, messageId: String) {
        launch {
            val isSelfDeletionOutgoing = outgoingTimerSelfDeletingMessagesState.value[conversationId to messageId] != null
            if (isSelfDeletionOutgoing) return@launch

            messageRepository.getMessageById(conversationId, messageId).map { message ->
                require(message is Message.Ephemeral)

                enqueueMessageDeletion(message)
            }
        }
    }

    private fun enqueueMessageDeletion(message: Message.Ephemeral) {
        userSessionCoroutineScope.launch {
            val selfDeletingMessageTimer = SelfDeletingMessageTimer(
                coroutineScope = userSessionCoroutineScope
            )

            for (timerEvent in selfDeletingMessageTimer.startTimer(message.expireAfterMillis)) {
                when (timerEvent) {
                    is SelfDeletionTimerState.OnGoing -> {
                        outgoingTimerSelfDeletingMessagesState.update { currentState ->
                            currentState + ((message.conversationId to message.id) to timerEvent.timeLeft)
                        }
                    }

                    SelfDeletionTimerState.Finished -> {
                        messageRepository.deleteMessage(message.id, message.conversationId)
                        outgoingTimerSelfDeletingMessagesState.update { currentState ->
                            currentState - (message.conversationId to message.id)
                        }
                    }
                }
            }
        }
    }

    override fun observePendingMessageDeletionState() = outgoingTimerSelfDeletingMessagesState

    override fun enqueuePendingSelfDeletionMessages() {
        launch {
            messageRepository.getEphemeralMessages().onSuccess { ephemeralMessages ->
                ephemeralMessages.forEach { ephemeralMessage ->
                    require(ephemeralMessage is Message.Ephemeral)

                    enqueueMessageDeletion(message = ephemeralMessage)
                }
            }
        }
    }

}

internal class SelfDeletingMessageTimer(
    private val coroutineScope: CoroutineScope
) : CoroutineScope by coroutineScope {
    private companion object {
        const val TIMER_UPDATE_INTERVAL_IN_MILLIS = 1000L
    }

    fun startTimer(expireAfterMillis: Long) = produce {
        var elapsedTime = 0L

        while (elapsedTime < expireAfterMillis) {
            delay(TIMER_UPDATE_INTERVAL_IN_MILLIS)
            elapsedTime += TIMER_UPDATE_INTERVAL_IN_MILLIS

            send(SelfDeletionTimerState.OnGoing(expireAfterMillis - elapsedTime))
        }

        send(SelfDeletionTimerState.Finished)
    }
}


sealed class SelfDeletionTimerState {
    data class OnGoing(val timeLeft: Long) : SelfDeletionTimerState()

    object Finished : SelfDeletionTimerState()
}

