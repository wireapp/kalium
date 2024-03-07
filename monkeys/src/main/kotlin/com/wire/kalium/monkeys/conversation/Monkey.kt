/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */
package com.wire.kalium.monkeys.conversation

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.client.ClientType
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationOptions
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.sync.SyncState
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.auth.AddAuthenticatedUserUseCase
import com.wire.kalium.logic.feature.auth.AuthenticationResult
import com.wire.kalium.logic.feature.auth.AuthenticationScope
import com.wire.kalium.logic.feature.auth.autoVersioningAuth.AutoVersionAuthScopeUseCase
import com.wire.kalium.logic.feature.client.RegisterClientResult
import com.wire.kalium.logic.feature.client.RegisterClientUseCase
import com.wire.kalium.logic.feature.conversation.CreateConversationResult
import com.wire.kalium.logic.feature.conversation.CreateGroupConversationUseCase
import com.wire.kalium.logic.feature.publicuser.GetAllContactsResult
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.monkeys.logger
import com.wire.kalium.monkeys.model.Backend
import com.wire.kalium.monkeys.model.MonkeyId
import com.wire.kalium.monkeys.model.UserCount
import com.wire.kalium.monkeys.model.UserData
import com.wire.kalium.monkeys.pool.ConversationPool
import com.wire.kalium.monkeys.pool.MonkeyPool
import com.wire.kalium.monkeys.pool.resolveUserCount
import kotlinx.coroutines.flow.first

sealed class MonkeyType {
    data class Internal(val user: UserData) : MonkeyType()
    data class External(val userId: UserId) : MonkeyType()

    fun userId(): UserId = when (this) {
        is External -> this.userId
        is Internal -> this.user.userId
    }

    /**
     * Ensures that the monkey type is internal and return its user data
     */
    fun userData(): UserData = when (this) {
        is External -> error("This is an external Monkey and can't perform this operation")
        is Internal -> this.user
    }
}

/**
 * A monkey is a user puppeteered by the test framework.
 * It contains the basic user data and provides
 * the [monkeyState] which we can use to perform actions.
 */
@Suppress("TooManyFunctions")
class Monkey(val monkeyType: MonkeyType, val internalId: MonkeyId) {
    companion object {
        // this means there are users within the team not managed by IM
        // We can still send messages and add them to groups but not act on their behalf
        // MonkeyId is irrelevant for external users as we will never be able to act on their behalf
        fun external(userId: UserId) = Monkey(MonkeyType.External(userId), MonkeyId(-1, "", -1))
        fun internal(user: UserData, monkeyId: MonkeyId) = Monkey(MonkeyType.Internal(user), monkeyId)
    }

    private var monkeyState: MonkeyState = MonkeyState.NotReady

    fun isSessionActive(): Boolean {
        return monkeyState is MonkeyState.Ready
    }

    override fun equals(other: Any?): Boolean {
        return other != null && when (other) {
            is Monkey -> other.monkeyType.userId() == this.monkeyType.userId()
            else -> false
        }
    }

    override fun hashCode(): Int {
        return this.monkeyType.userId().hashCode()
    }

    /**
     * Logs user in and register client (if not registered)
     */
    suspend fun login(coreLogic: CoreLogic, callback: (Monkey) -> Unit) {
        val authScope = getAuthScope(coreLogic, this.monkeyType.userData().team.backend)
        val email = this.monkeyType.userData().email
        val password = this.monkeyType.userData().password
        val loginResult = authScope.login(email, password, false)
        if (loginResult !is AuthenticationResult.Success) {
            error("User creds didn't work ($email, $password)")
        }

        coreLogic.globalScope {
            val storeResult = addAuthenticatedAccount(
                serverConfigId = loginResult.serverConfigId,
                ssoId = loginResult.ssoID,
                authTokens = loginResult.authData,
                proxyCredentials = loginResult.proxyCredentials,
                replace = true
            )
            if (storeResult !is AddAuthenticatedUserUseCase.Result.Success) {
                error("Failed to store user. $storeResult")
            }
        }
        val sessionScope = coreLogic.getSessionScope(loginResult.authData.userId)
        val registerClientParam = RegisterClientUseCase.RegisterClientParam(
            password = this.monkeyType.userData().password, capabilities = emptyList(), clientType = ClientType.Temporary
        )
        val registerResult = sessionScope.client.getOrRegister(registerClientParam)
        if (registerResult is RegisterClientResult.Failure) {
            this.monkeyState = MonkeyState.NotReady
            error("Failed registering client of monkey ${this.monkeyType.userData().email}: $registerResult")
        }
        var isFinished: Boolean
        do {
            val state = sessionScope.observeSyncState().first { it !is SyncState.Waiting && it !is SyncState.SlowSync }
            when (state) {
                is SyncState.Failed -> {
                    this.monkeyState = MonkeyState.NotReady
                    logger.w("Failed logging in: ${state.cause}. Retrying? ${state.cause.isRetryable}")
                    isFinished = !state.cause.isRetryable
                }
                is SyncState.GatheringPendingEvents,
                is SyncState.Live -> {
                    this.monkeyState = MonkeyState.Ready(sessionScope)
                    callback(this)
                    isFinished = true
                }
                else -> error("This should have been done")
            }
        } while (!isFinished)
    }

    private suspend fun connectedMonkeys(): List<UserId> {
        val self = this
        return this.monkeyState.readyThen {
            val connectedUsersResult = users.getAllKnownUsers().first()
            if (connectedUsersResult is GetAllContactsResult.Success) {
                connectedUsersResult.allContacts.map { it.id }
            } else {
                error("Failed getting connected users of monkey ${self.monkeyType.userId()}: $connectedUsersResult")
            }
        }
    }

    suspend fun logout(callback: (Monkey) -> Unit) {
        this.monkeyState.readyThen { logout(LogoutReason.SELF_SOFT_LOGOUT) }
        this.monkeyState = MonkeyState.NotReady
        callback(this)
    }

    suspend fun randomPeer(monkeyPool: MonkeyPool): Monkey {
        return monkeyPool.get(this.connectedMonkeys().randomOrNull() ?: error("Monkey ${this.monkeyType.userId()} not connected to anyone"))
    }

    suspend fun randomPeers(userCount: UserCount, monkeyPool: MonkeyPool, filterOut: List<UserId> = listOf()): List<Monkey> {
        val connectedMonkeys = this.connectedMonkeys()
        val count = resolveUserCount(userCount, connectedMonkeys.count().toUInt())
        return connectedMonkeys.filterNot { filterOut.contains(it) }.shuffled().map(monkeyPool::get).take(count.toInt())
    }

    suspend fun sendRequest(anotherMonkey: Monkey) {
        this.monkeyState.readyThen {
            connection.sendConnectionRequest(anotherMonkey.monkeyType.userId())
        }
    }

    suspend fun acceptRequest(anotherMonkey: Monkey) {
        this.monkeyState.readyThen {
            connection.acceptConnectionRequest(anotherMonkey.monkeyType.userId())
        }
    }

    suspend fun rejectRequest(anotherMonkey: Monkey) {
        this.monkeyState.readyThen {
            connection.ignoreConnectionRequest(anotherMonkey.monkeyType.userId())
        }
    }

    suspend fun pendingConnectionRequests(): List<ConversationDetails.Connection> {
        return this.monkeyState.readyThen {
            conversations.observePendingConnectionRequests().first().filter { it.connection.status == ConnectionState.PENDING }
        }
    }

    suspend fun <T> makeReadyThen(coreLogic: CoreLogic, monkeyPool: MonkeyPool, func: suspend Monkey.() -> T): T {
        if (this.monkeyState is MonkeyState.NotReady) {
            this.login(coreLogic, monkeyPool::loggedIn)
        }
        return this.func()
    }

    suspend fun createConversation(
        name: String,
        monkeyList: List<Monkey>,
        protocol: ConversationOptions.Protocol,
        isDestroyable: Boolean = true
    ): MonkeyConversation {
        val self = this
        return this.monkeyState.readyThen {
            val result = conversations.createGroupConversation(
                name, monkeyList.map { it.monkeyType.userId() }, ConversationOptions(protocol = protocol)
            )
            if (result is CreateGroupConversationUseCase.Result.Success) {
                MonkeyConversation(self, result.conversation, isDestroyable, monkeyList)
            } else {
                if (result is CreateGroupConversationUseCase.Result.UnknownFailure) {
                    error("${self.monkeyType.userId()} could not create group $name: ${result.cause}")
                } else {
                    error("${self.monkeyType.userId()} could not create group $name: $result")
                }
            }
        }
    }

    suspend fun leaveConversation(conversationId: ConversationId) {
        if (this.monkeyType.userId() == ConversationPool.conversationCreator(conversationId)?.monkeyType?.userId()) {
            error("Creator of the group can't leave")
        }
        this.monkeyState.readyThen {
            conversations.leaveConversation(conversationId)
        }
    }

    suspend fun destroyConversation(conversationId: ConversationId) {
        if (this.monkeyType.userId() != ConversationPool.conversationCreator(conversationId)?.monkeyType?.userId()) {
            error("Only the creator can destroy a group")
        }

        this.monkeyState.readyThen {
            conversations.deleteTeamConversation
        }
    }

    suspend fun addMonkeysToConversation(conversationId: ConversationId, monkeys: List<Monkey>) {
        this.monkeyState.readyThen {
            conversations.addMemberToConversationUseCase(conversationId, monkeys.map { it.monkeyType.userId() })
        }
    }

    suspend fun removeMonkeyFromConversation(id: ConversationId, monkey: Monkey) {
        this.monkeyState.readyThen {
            conversations.removeMemberFromConversation(
                id, monkey.monkeyType.userId()
            )
        }
    }

    suspend fun sendDirectMessageTo(anotherMonkey: Monkey, message: String) {
        val self = this.monkeyType.userId()
        this.monkeyState.readyThen {
            val result = conversations.getOrCreateOneToOneConversationUseCase(anotherMonkey.monkeyType.userId())
            if (result is CreateConversationResult.Success) {
                messages.sendTextMessage(result.conversation.id, message)
            } else {
                error("$self failed contacting ${anotherMonkey.monkeyType.userId()}: $result")
            }
        }
    }

    suspend fun sendMessageTo(conversationId: ConversationId, message: String) {
        this.monkeyState.readyThen {
            val result = messages.sendTextMessage(conversationId, message)
            if (result is Either.Left) {
               error("Error sending message: ${result.value}")
            }
        }
    }
}

private sealed class MonkeyState {
    data object NotReady : MonkeyState()
    data class Ready(val userSessionScope: UserSessionScope) : MonkeyState()

    suspend fun <T> readyThen(func: suspend UserSessionScope.() -> T): T {
        return when (this) {
            is Ready -> this.userSessionScope.func()
            is NotReady -> error("Monkey not ready")
        }
    }
}

private suspend fun getAuthScope(coreLogic: CoreLogic, backend: Backend): AuthenticationScope {
    val result = coreLogic.versionedAuthenticationScope(
        ServerConfig.Links(
            api = backend.api,
            accounts = backend.accounts,
            webSocket = backend.webSocket,
            blackList = backend.blackList,
            teams = backend.teams,
            website = backend.website,
            title = backend.title,
            isOnPremises = true,
            apiProxy = null
        )
    ).invoke(null)
    if (result !is AutoVersionAuthScopeUseCase.Result.Success) {
        error("Failed getting AuthScope: $result")
    }
    return result.authenticationScope
}
