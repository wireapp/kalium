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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlin.coroutines.CoroutineContext


interface EphemeralMessageDeletionHandler {
    fun startSelfDeletion(conversationId: ConversationId, messageId: String)

    fun observePendingMessageDeletionState(): Flow<Map<Pair<ConversationId, String>, SelfDeletionTimeLeft>>

    fun enqueuePendingSelfDeletionMessages()
}

internal class EphemeralMessageDeletionHandlerImpl(
    private val userSessionCoroutineScope: CoroutineScope,
    private val kaliumDispatcher: KaliumDispatcher = KaliumDispatcherImpl,
    private val messageRepository: MessageRepository
) : EphemeralMessageDeletionHandler, CoroutineScope by userSessionCoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = kaliumDispatcher.default

    private val ephemeralMessageDeletionTimeLeftMapMutex = Mutex()
    private val ephemeralMessageDeletionTimeLeftMap: MutableStateFlow<Map<Pair<ConversationId, String>, SelfDeletionTimeLeft>> =
        MutableStateFlow(emptyMap())

    override fun startSelfDeletion(conversationId: ConversationId, messageId: String) {
        userSessionCoroutineScope.launch {
            // in case client start deletion couple of times for the message, we secure it with mutex
            // that will update the map with a corresponding key and default value, so that coroutine
            // launched after the first winning the race simply returns
            ephemeralMessageDeletionTimeLeftMapMutex.withLock {
                val isSelfDeletionOutgoing = ephemeralMessageDeletionTimeLeftMap.value[conversationId to messageId] != null
                if (isSelfDeletionOutgoing) return@launch

                ephemeralMessageDeletionTimeLeftMap.update { currentState ->
                    currentState + ((conversationId to messageId) to SelfDeletionTimeLeft(0))
                }
            }

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

            for (timerEvent in selfDeletingMessageTimer.startTimer(message.actualExpireAfterMillis())) {
                when (timerEvent) {
                    is SelfDeletionTimerState.Started -> {
                        if (!message.isDeletionScheduledInThePast()) {
                            messageRepository.markSelfDeletionStartDate(
                                message.conversationId,
                                message.id,
                                Clock.System.now().toEpochMilliseconds()
                            )
                        }
                    }

                    is SelfDeletionTimerState.OnGoing -> {
                        println("time left ${timerEvent.timeLeft.timeLeftInMs}")
                        ephemeralMessageDeletionTimeLeftMapMutex.withLock {
                            ephemeralMessageDeletionTimeLeftMap.update { currentState ->
                                currentState + ((message.conversationId to message.id) to timerEvent.timeLeft)
                            }
                        }
                    }

                    is SelfDeletionTimerState.Finished -> {
                        messageRepository.deleteMessage(message.id, message.conversationId)
                        ephemeralMessageDeletionTimeLeftMapMutex.withLock {
                            ephemeralMessageDeletionTimeLeftMap.update { currentState ->
                                currentState - (message.conversationId to message.id)
                            }
                        }
                    }

                }
            }
        }
    }

    override fun observePendingMessageDeletionState() = ephemeralMessageDeletionTimeLeftMap

    override fun enqueuePendingSelfDeletionMessages() {
        launch {
            messageRepository.getEphemeralMessages()
                .onSuccess { ephemeralMessages ->
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
        send(SelfDeletionTimerState.Started(Clock.System.now().toEpochMilliseconds()))

        var elapsedTime = 0L

        while (elapsedTime < expireAfterMillis) {
            delay(TIMER_UPDATE_INTERVAL_IN_MILLIS)

            send(SelfDeletionTimerState.OnGoing(SelfDeletionTimeLeft(expireAfterMillis - elapsedTime)))

            elapsedTime += TIMER_UPDATE_INTERVAL_IN_MILLIS
        }

        send(SelfDeletionTimerState.Finished)
    }
}


sealed class SelfDeletionTimerState {

    data class Started(val deletionStartDateInMs: Long) : SelfDeletionTimerState()
    data class OnGoing(val timeLeft: SelfDeletionTimeLeft) : SelfDeletionTimerState()

    object Finished : SelfDeletionTimerState()
}

class SelfDeletionTimeLeft(val timeLeftInMs: Long){

}
