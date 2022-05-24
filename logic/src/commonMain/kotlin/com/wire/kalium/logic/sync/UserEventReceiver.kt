package com.wire.kalium.logic.sync

import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.kaliumLogger

class UserEventReceiver(
    private val connectionRepository: ConnectionRepository,
) : EventReceiver<Event.User> {

    override suspend fun onEvent(event: Event.User) {
        when (event) {
            is Event.User.NewConnection -> handleNewConnection(event)
        }
    }

    private suspend fun handleNewConnection(event: Event.User.NewConnection) =
        connectionRepository.insertConnectionFromEvent(event)
            .onFailure { kaliumLogger.e("$TAG - failure on new connection event: $it") }

    private companion object {
        const val TAG = "UserEventReceiver"
    }
}
