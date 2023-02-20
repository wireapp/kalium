package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.map
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.launch

class EnqueueMessageSelfDeletionUseCase(coroutineScope: CoroutineScope) {

    private val selfDeletingMessageManager = SelfDeletingMessageManager(coroutineScope)

    fun invoke(conversationId: ConversationId, messageId: String) {

    }

}


internal class SelfDeletingMessageManager(
    private val coroutineScope: CoroutineScope,
    private val messageRepository: MessageRepository
) : CoroutineScope by coroutineScope {

    // hot channel map
    private val map: MutableMap<Pair<ConversationId, String>, ReceiveChannel<String>> = mutableMapOf()

    fun enqueue(conversationId: ConversationId, messageId: String) {
        launch {
            val requestedMessage = messageRepository.getMessageById(conversationId, messageId)

            requestedMessage.map { message ->
                if (message is Message.Ephemeral) {
                    val outgoingDeletionTimer = map[conversationId to messageId]

                    if (outgoingDeletionTimer == null) {
                        println("already outgoing")
                    } else {
                        map[conversationId to messageId] = produce {
                            val isObligateForDeletion = message.expireAfterMillis
                        }
                    }
                } else {
                    throw IllegalStateException("Not an ephemeral message")
                }
            }
        }
    }

    fun observePendingMessageDeletionState() {

    }

}
