package com.wire.kalium.logic.sync.receiver

import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.CurrentClientIdProvider
import com.wire.kalium.logic.feature.auth.LogoutUseCase
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger

interface UserEventReceiver : EventReceiver<Event.User>

class UserEventReceiverImpl internal constructor(
    private val connectionRepository: ConnectionRepository,
    private val conversationRepository: ConversationRepository,
    private val userRepository: UserRepository,
    private val logout: LogoutUseCase,
    private val selfUserId: UserId,
    private val currentClientIdProvider: CurrentClientIdProvider,
) : UserEventReceiver {

    override suspend fun onEvent(event: Event.User) {
        when (event) {
            is Event.User.NewConnection -> handleNewConnection(event)
            is Event.User.ClientRemove -> handleClientRemove(event)
            is Event.User.UserDelete -> handleUserDelete(event)
            is Event.User.Update -> handleUserUpdate(event)
        }
    }

    private suspend fun handleUserUpdate(event: Event.User.Update) {
        userRepository.updateUserFromEvent(event)
            .onSuccess { kaliumLogger.d("$TAG - user was updated from event: $it") }
            .onFailure { kaliumLogger.e("$TAG - failure updating user from event: $it") }
    }

    private suspend fun handleNewConnection(event: Event.User.NewConnection) =
        connectionRepository.insertConnectionFromEvent(event)
            .onFailure { kaliumLogger.e("$TAG - failure on new connection event: $it") }

    private suspend fun handleClientRemove(event: Event.User.ClientRemove) {
        currentClientIdProvider().map { currentClientId ->
            if (currentClientId == event.clientId)
                logout(LogoutReason.REMOVED_CLIENT)
        }
    }

    private suspend fun handleUserDelete(event: Event.User.UserDelete) {
        if (selfUserId == event.userId) {
            logout(LogoutReason.DELETED_ACCOUNT)
        } else {
            userRepository.removeUser(event.userId)
                .onSuccess { conversationRepository.deleteUserFromConversations(event.userId) }
                .onFailure { kaliumLogger.e("$TAG - failure on user delete event: $it") }
        }
    }

    private companion object {
        const val TAG = "UserEventReceiver"
    }
}
