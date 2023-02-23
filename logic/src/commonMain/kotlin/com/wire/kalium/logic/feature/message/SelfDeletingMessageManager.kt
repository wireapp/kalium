package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.functional.map
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext


interface SelfDeletingMessageManager {
    fun enqueue(conversationId: ConversationId, messageId: String)

    fun observePendingMessageDeletionState(): Flow<Map<String, Long>>
}

internal class SelfDeletingMessageManagerImpl(
    private val coroutineScope: CoroutineScope,
    private val kaliumDispatcher: KaliumDispatcher = KaliumDispatcherImpl,
    private val messageRepository: MessageRepository
) : SelfDeletingMessageManager, CoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = kaliumDispatcher.default.limitedParallelism(1) + SupervisorJob()

    private val outgoingSelfDeletingMessages: MutableStateFlow<Map<Pair<ConversationId, String>, SelfDeletingMessage>> =
        MutableStateFlow(emptyMap())

    override fun enqueue(conversationId: ConversationId, messageId: String) {
        launch {
            if (outgoingSelfDeletingMessages.value[conversationId to messageId] != null) {
                println("message deletion is already pending")
            } else {
                val selfDeletingMessage = SelfDeletingMessage(
                    messageRepository = messageRepository,
                    conversationId = conversationId,
                    messageId = messageId,
                    coroutineScope = coroutineScope
                )

                outgoingSelfDeletingMessages.update { currentMap ->
                    currentMap + ((conversationId to messageId) to selfDeletingMessage)
                }

                selfDeletingMessage.startDeletion()
            }
        }
    }

    override fun observePendingMessageDeletionState(): Flow<Map<String, Long>> =
        outgoingSelfDeletingMessages.flatMapLatest { outgoingSelfDeletingMessages ->
            combine(outgoingSelfDeletingMessages.map { entry ->
                val (_, messageId) = entry.key
                val selfDeletingMessage = entry.value

                selfDeletingMessage.timeLeft.map { timeLeft ->
                    messageId to timeLeft
                }
            }) {
                it.associate { (messageId, timeLeft) -> messageId to timeLeft }
            }
        }

}

internal class SelfDeletingMessage(
    val conversationId: ConversationId,
    val messageId: String,
    private val messageRepository: MessageRepository,
    private val coroutineScope: CoroutineScope
) : CoroutineScope by coroutineScope {

    private val mutableTimeLeft: MutableStateFlow<Long> = MutableStateFlow(0)
    val timeLeft: StateFlow<Long> = mutableTimeLeft

    fun startDeletion() {
        launch {
            messageRepository.getMessageById(conversationId, messageId).map { message ->
                require(message is Message.Ephemeral)

                var elapsedTime = 0L

                while (elapsedTime < message.expireAfterMillis) {
                    delay(1000)
                    elapsedTime += 1000

                    mutableTimeLeft.value = message.expireAfterMillis - elapsedTime
                }

                messageRepository.deleteMessage(messageId, conversationId)
            }
        }
    }
}
