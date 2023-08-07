/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
import com.wire.kalium.logic.data.conversation.ConversationOptions
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.auth.AddAuthenticatedUserUseCase
import com.wire.kalium.logic.feature.auth.AuthenticationResult
import com.wire.kalium.logic.feature.auth.AuthenticationScope
import com.wire.kalium.logic.feature.auth.autoVersioningAuth.AutoVersionAuthScopeUseCase
import com.wire.kalium.logic.feature.client.RegisterClientResult
import com.wire.kalium.logic.feature.client.RegisterClientUseCase
import com.wire.kalium.logic.feature.conversation.CreateGroupConversationUseCase
import com.wire.kalium.logic.feature.conversation.GetOneToOneConversationUseCase
import com.wire.kalium.monkeys.importer.Backend
import com.wire.kalium.monkeys.importer.UserData
import com.wire.kalium.monkeys.pool.ConversationPool
import java.util.concurrent.ConcurrentHashMap

/**
 * A monkey is a user puppeteered by the test framework.
 * It contains the basic [user] data and provides
 * the [monkeyState] which we can use to perform actions.
 */
class Monkey(
    val user: UserData,
) {
    private var monkeyState: MonkeyState = MonkeyState.NotReady
    private var connectedMonkeys: ConcurrentHashMap<UserId, Monkey> = ConcurrentHashMap()

    fun isSessionActive(): Boolean {
        return monkeyState is MonkeyState.Ready
    }

    /**
     * Logs user in and register client (if not registered)
     */
    suspend fun login(coreLogic: CoreLogic, callback: (Monkey) -> Unit) {
        val authScope = getAuthScope(coreLogic, this.user.backend)
        val email = this.user.email
        val password = this.user.password
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
        this.monkeyState = MonkeyState.Ready(coreLogic.getSessionScope(loginResult.authData.userId))
        val registerClientParam = RegisterClientUseCase.RegisterClientParam(
            password = this.user.password,
            capabilities = emptyList(),
            clientType = ClientType.Temporary
        )
        val registerResult = this.monkeyState.readyThen { client.getOrRegister(registerClientParam) }
        if (registerResult is RegisterClientResult.Failure) {
            this.monkeyState = MonkeyState.NotReady
            error("Failed registering client of monkey ${this.user.email}: $registerResult")
        }
        callback(this)
    }

    suspend fun logout(callback: (Monkey) -> Unit) {
        this.monkeyState.readyThen { logout(LogoutReason.SELF_SOFT_LOGOUT) }
        callback(this)
    }

    fun randomPeer() {
        this.connectedMonkeys.values.randomOrNull() ?: error("Monkey ${this.user.email} not connected to anyone")
    }

    fun connectTo(anotherMonkey: Monkey) {
        this.connectedMonkeys[anotherMonkey.user.userId] = anotherMonkey
    }

    fun disconnectFrom(anotherMonkey: Monkey) {
        this.connectedMonkeys.remove(anotherMonkey.user.userId)
    }

    suspend fun createConversation(name: String, monkeyList: List<Monkey>, protocol: ConversationOptions.Protocol): MonkeyConversation {
        val self = this
        return this.monkeyState.readyThen {
            val result = conversations.createGroupConversation(
                name, monkeyList.map { it.user.userId },
                ConversationOptions(protocol = protocol)
            )
            if (result is CreateGroupConversationUseCase.Result.Success) {
                MonkeyConversation(self, result.conversation)
            } else {
                error("${self.user.email} could not create group $name")
            }
        }
    }

    suspend fun leaveConversation(conversationId: ConversationId) {
        if (this.user.userId == ConversationPool.conversationCreator(conversationId)?.user?.userId) {
            error("Creator of the group can't leave")
        }
        this.monkeyState.readyThen {
            conversations.leaveConversation(conversationId)
        }
    }

    suspend fun destroyConversation(conversationId: ConversationId) {
        if (this.user.userId != ConversationPool.conversationCreator(conversationId)?.user?.userId) {
            error("Only the creator can destroy a group")
        }

        this.monkeyState.readyThen {
            conversations.deleteTeamConversation
        }
    }

    suspend fun addMonkeysToConversation(conversationId: ConversationId, monkeys: List<Monkey>) {
        this.monkeyState.readyThen {
            conversations.addMemberToConversationUseCase(
                conversationId,
                monkeys.map { it.user.userId })
        }
    }

    suspend fun sendDirectMessageTo(anotherMonkey: Monkey, message: String) {
        val self = this.user.email
        this.monkeyState.readyThen {
            conversations.getOneToOneConversation(anotherMonkey.user.userId).collect { result ->
                if (result is GetOneToOneConversationUseCase.Result.Success) {
                    messages.sendTextMessage(
                        result.conversation.id, message
                    )
                } else {
                    error("$self failed contacting ${anotherMonkey.user.email}: $result")
                }
            }
        }
    }

    suspend fun sendMessageTo(conversationId: ConversationId, message: String) {
        this.monkeyState.readyThen {
            messages.sendTextMessage(conversationId, message)
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
    ).invoke()
    if (result !is AutoVersionAuthScopeUseCase.Result.Success) {
        error("Failed getting AuthScope: $result")
    }
    return result.authenticationScope
}
