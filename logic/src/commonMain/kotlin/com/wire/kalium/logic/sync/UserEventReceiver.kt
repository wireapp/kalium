package com.wire.kalium.logic.sync

import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.event.Event

class UserEventReceiver(
    private val conversationRepository: ConversationRepository,
) : EventReceiver<Event.User> {

    override suspend fun onEvent(event: Event.User) {
        when (event) {
            is Event.User.Connection -> handleNewConnection(event)
        }
    }

    private fun handleNewConnection(event: Event.User.Connection): Unit {
        // TODO put connection to conversations list
    }

    private companion object {
        const val TAG = "UserEventReceiver"
    }
}
