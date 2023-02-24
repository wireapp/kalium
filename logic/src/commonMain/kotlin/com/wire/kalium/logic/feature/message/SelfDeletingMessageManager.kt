package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.coroutines.CoroutineContext


interface SelfDeletingMessageManager {
    fun startSelfDeletion(conversationId: ConversationId, messageId: String)

    fun observePendingMessageDeletionState(): Flow<Map<String, Long>>

    fun enqueuePendingSelfDeletionMessages()
}

internal class SelfDeletingMessageManagerImpl(
    private val coroutineScope: CoroutineScope,
    private val kaliumDispatcher: KaliumDispatcher = KaliumDispatcherImpl,
    private val messageRepository: MessageRepository
) : SelfDeletingMessageManager, CoroutineScope by coroutineScope {
    override val coroutineContext: CoroutineContext
        get() = kaliumDispatcher.default.limitedParallelism(1) + SupervisorJob()

    private val outgoingSelfDeletingMessagesTimeLeft: MutableStateFlow<Map<Pair<ConversationId, String>, SelfDeletingMessage>> =
        MutableStateFlow(emptyMap())

    override fun startSelfDeletion(conversationId: ConversationId, messageId: String) {
        launch {
            val isSelfDeletionOutgoing = outgoingSelfDeletingMessagesTimeLeft.value[conversationId to messageId] != null
            if (isSelfDeletionOutgoing) return@launch

            messageRepository.getMessageById(conversationId, messageId).map { message ->
                require(message is Message.Ephemeral)

                enqueueMessage(message)
            }
        }
    }

    private fun enqueueMessage(message: Message.Ephemeral) {
        launch {
            val selfDeletingMessage = createOutgoingSelfDeletingMessage(message.conversationId, message.id)
            addOutgoingSelfDeletingMessage(selfDeletingMessage)

            val isEnqueuedForFirstTime = message.selfDeletionDate == null
            if (isEnqueuedForFirstTime) {
                messageRepository.markSelfDeletionDate(
                    conversationId = message.conversationId,
                    messageUuid = message.id,
                    deletionDate = Clock.System.now().toEpochMilliseconds() + message.expireAfterMillis()
                )
            }

            selfDeletingMessage.startSelfDeletionTimer(
                expireAfterMillis = message.expireAfterMillis()
            )

            selfDeletingMessage.timeLeft.filterIsInstance<SelfDeletingMessage.SelfDeletionTimerState>().collect {
                messageRepository.deleteMessage(message.id, message.conversationId)
                removeOutgoingSelfDeletingMessage(message.conversationId, message.id)
            }
        }
    }

    private fun addOutgoingSelfDeletingMessage(selfDeletingMessage: SelfDeletingMessage) {
        outgoingSelfDeletingMessagesTimeLeft.update { currentMap ->
            currentMap + ((selfDeletingMessage.conversationId to selfDeletingMessage.messageId) to selfDeletingMessage)
        }
    }

    private fun removeOutgoingSelfDeletingMessage(conversationId: ConversationId, messageId: String) {
        outgoingSelfDeletingMessagesTimeLeft.update { currentMap ->
            currentMap - (conversationId to messageId)
        }
    }

    private fun createOutgoingSelfDeletingMessage(conversationId: ConversationId, messageId: String): SelfDeletingMessage {
        return SelfDeletingMessage(
            messageId = messageId,
            coroutineScope = coroutineScope
        )
    }

    override fun observePendingMessageDeletionState(): Flow<Map<String, Long>> =
        outgoingSelfDeletingMessagesTimeLeft.flatMapLatest { outgoingSelfDeletingMessages ->
            val observableTimeLeftOfMessages = createObservableTimeLeftOfMessages(outgoingSelfDeletingMessages)
            combineTimeLeftOfSelfDeletingMessage(observableTimeLeftOfMessages)
        }

    private fun createObservableTimeLeftOfMessages(
        outgoingSelfDeletingMessages: Map<Pair<ConversationId, String>, SelfDeletingMessage>
    ): List<Flow<Pair<String, Long>>> {
        return outgoingSelfDeletingMessages.map { (conversationIdWithMessageId, selfDeletingMessage) ->
            val (_, messageId) = conversationIdWithMessageId

            selfDeletingMessage.timeLeft.map { timeLeft -> messageId to timeLeft }
        }
    }

    private fun combineTimeLeftOfSelfDeletingMessage(observableTimeLeftOfMessages: List<Flow<Pair<String, Long>>>): Flow<Map<String, Long>> {
        return combine(observableTimeLeftOfMessages) {
            it.associate { (messageId, timeLeft) -> messageId to timeLeft }
        }
    }

    override fun enqueuePendingSelfDeletionMessages() {
        launch {
            messageRepository.getEphemeralMessages().onSuccess { ephemeralMessages ->
                ephemeralMessages.forEach { ephemeralMessage ->
                    require(ephemeralMessage is Message.Ephemeral)

                    enqueueMessage(message = ephemeralMessage)
                }
            }
        }
    }

}

internal class SelfDeletingMessage(
    val messageId: String,
    private val coroutineScope: CoroutineScope
) : CoroutineScope by coroutineScope {
    private companion object {
        const val TIMER_UPDATE_INTERVAL_IN_MILLIS = 1000L
    }

    private val mutableTimeLeft: MutableStateFlow<Pair<String, SelfDeletionTimerState>> =
        MutableStateFlow(Pair(messageId, SelfDeletionTimerState.OnGoing(0)))
    val timeLeft: StateFlow<Pair<String, SelfDeletionTimerState>> = mutableTimeLeft

    fun startSelfDeletionTimer(expireAfterMillis: Long) {
        launch {
            var elapsedTime = 0L

            while (elapsedTime < expireAfterMillis) {
                delay(TIMER_UPDATE_INTERVAL_IN_MILLIS)
                elapsedTime += TIMER_UPDATE_INTERVAL_IN_MILLIS

                mutableTimeLeft.value = messageId to SelfDeletionTimerState.OnGoing(expireAfterMillis - elapsedTime)
            }

            mutableTimeLeft.value = messageId to SelfDeletionTimerState.Finished
        }
    }


    sealed class SelfDeletionTimerState {
        data class OnGoing(val timeLeft: Long) : SelfDeletionTimerState()

        object Finished : SelfDeletionTimerState()
    }
}
