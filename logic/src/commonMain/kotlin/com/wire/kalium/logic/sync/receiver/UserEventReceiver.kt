package com.wire.kalium.logic.sync.receiver

import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.feature.auth.LogoutUseCase
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.kaliumLogger

interface UserEventReceiver : EventReceiver<Event.User>

class UserEventReceiverImpl(
    private val connectionRepository: ConnectionRepository,
    private val logoutUseCase: LogoutUseCase,
    private val clientRepository: ClientRepository
) : UserEventReceiver {

    override suspend fun onEvent(event: Event.User) {
        when (event) {
            is Event.User.NewConnection -> handleNewConnection(event)
            is Event.User.ClientRemove -> handleClientRemove(event)
            is Event.User.UserDelete -> handleUserDelete(event)
        }
    }

    private suspend fun handleNewConnection(event: Event.User.NewConnection) =
        connectionRepository.insertConnectionFromEvent(event)
            .onFailure { kaliumLogger.e("$TAG - failure on new connection event: $it") }

    private suspend fun handleClientRemove(event: Event.User.ClientRemove) {
        clientRepository.currentClientId().map { currentClientId ->
            if (currentClientId.value == event.id)
                logoutUseCase(LogoutReason.REMOVED_CLIENT)
        }
    }

    private suspend fun handleUserDelete(event: Event.User.UserDelete) {
        logoutUseCase(LogoutReason.DELETED_ACCOUNT)
    }

    private companion object {
        const val TAG = "UserEventReceiver"
    }
}
