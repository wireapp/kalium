package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.id.ConversationId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce

class EnqueueMessageSelfDeletionUseCase(coroutineScope: CoroutineScope) {

    private val selfDeletingMessageManager = SelfDeletingMessageManager(coroutineScope)

    fun invoke(conversationId: ConversationId, messageId: String) {

    }

}


internal class SelfDeletingMessageManager(
    private val coroutineScope: CoroutineScope
) : CoroutineScope by coroutineScope {

    // hot channel map
    private val map: MutableMap<Pair<ConversationId, String>, ReceiveChannel<String>> = mutableMapOf()

    fun enqueue(conversationId: ConversationId, messageId: String) {
        val outgoingDeletionTimer = map[conversationId to messageId]

        if (outgoingDeletionTimer == null) {
            println("already outgoing")
        } else {
            map[conversationId to messageId] = produce {
//                 val isObligateForDeletion =
            }
        }
    }

    fun observePendingMessageDeletionState() {

    }

}
