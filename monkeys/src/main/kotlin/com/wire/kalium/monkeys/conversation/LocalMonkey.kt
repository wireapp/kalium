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

import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.client.ClientType
import com.wire.kalium.logic.data.conversation.CreateConversationParam
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
import com.wire.kalium.logic.feature.conversation.createconversation.ConversationCreationResult
import com.wire.kalium.logic.feature.publicuser.GetAllContactsResult
import com.wire.kalium.monkeys.logger
import com.wire.kalium.monkeys.model.Backend
import com.wire.kalium.monkeys.model.ConversationDef
import com.wire.kalium.monkeys.model.MonkeyId
import com.wire.kalium.monkeys.model.UserCount
import com.wire.kalium.monkeys.pool.MonkeyPool
import kotlinx.coroutines.flow.first

/**
 * A monkey is a user puppeteered by the test framework.
 * It contains the basic user data and provides
 * the [monkeyState] which we can use to perform actions.
 */
@Suppress("TooManyFunctions")
class LocalMonkey(monkeyType: MonkeyType, internalId: MonkeyId) : Monkey(monkeyType, internalId) {
    private var monkeyState: MonkeyState = MonkeyState.NotReady

    override suspend fun isSessionActive(): Boolean {
        return monkeyState is MonkeyState.Ready
    }

    /**
     * Logs user in and register client (if not registered)
     */
    override suspend fun login(coreLogic: CoreLogic, callback: (Monkey) -> Unit) {
        val userData = this.monkeyType.userData()
        val secondFactor = userData.request2FA()
        val authScope = getAuthScope(coreLogic, userData.team.backend)
        val email = userData.email
        val password = userData.password
        val loginResult = authScope.login(
            userIdentifier = email, password = password, shouldPersistClient = false, secondFactorVerificationCode = secondFactor
        )
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
            password = userData.password,
            capabilities = emptyList(),
            clientType = ClientType.Temporary,
            secondFactorVerificationCode = secondFactor
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

                is SyncState.GatheringPendingEvents, is SyncState.Live -> {
                    this.monkeyState = MonkeyState.Ready(sessionScope)
                    callback(this)
                    isFinished = true
                }

                else -> error("This should have been done")
            }
        } while (!isFinished)
    }

    override suspend fun connectedMonkeys(): List<UserId> {
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

    override suspend fun logout(callback: (Monkey) -> Unit) {
        this.monkeyState.readyThen { logout(LogoutReason.SELF_SOFT_LOGOUT) }
        this.monkeyState = MonkeyState.NotReady
        callback(this)
    }

    override suspend fun sendRequest(anotherMonkey: Monkey) {
        this.monkeyState.readyThen {
            connection.sendConnectionRequest(anotherMonkey.monkeyType.userId())
        }
    }

    override suspend fun acceptRequest(anotherMonkey: Monkey) {
        this.monkeyState.readyThen {
            connection.acceptConnectionRequest(anotherMonkey.monkeyType.userId())
        }
    }

    override suspend fun rejectRequest(anotherMonkey: Monkey) {
        this.monkeyState.readyThen {
            connection.ignoreConnectionRequest(anotherMonkey.monkeyType.userId())
        }
    }

    override suspend fun pendingConnectionRequests(): List<UserId> {
        return this.monkeyState.readyThen {
            conversations.observePendingConnectionRequests().first().filter { it.connection.status == ConnectionState.PENDING }
                .map { it.otherUser?.id ?: error("Cannot get other user id from connection request") }
        }
    }

    private suspend fun <T> makeReadyThen(coreLogic: CoreLogic, monkeyPool: MonkeyPool, func: suspend Monkey.() -> T): T {
        if (this.monkeyState is MonkeyState.NotReady) {
            this.login(coreLogic, monkeyPool::loggedIn)
        }
        return this.func()
    }

    override suspend fun createPrefixedConversation(
        name: String,
        protocol: CreateConversationParam.Protocol,
        userCount: UserCount,
        coreLogic: CoreLogic,
        monkeyPool: MonkeyPool,
        preset: ConversationDef?
    ): MonkeyConversation {
        return this.makeReadyThen(coreLogic, monkeyPool) {
            val participants =
                preset?.initialMembers?.map { monkeyPool.getFromTeam(it.team, it.index) } ?: this.randomPeers(userCount, monkeyPool)
            createConversation(name, participants, protocol, false)
        }
    }

    override suspend fun warmUp(core: CoreLogic) {
        login(core) {}
        logout {}
    }

    override suspend fun createConversation(
        name: String,
        monkeyList: List<Monkey>,
        protocol: CreateConversationParam.Protocol,
        isDestroyable: Boolean
    ): MonkeyConversation {
        val self = this
        return this.monkeyState.readyThen {
            val result = conversations.createRegularGroup(
                name,
                monkeyList.map { it.monkeyType.userId() },
                CreateConversationParam(
                    protocol = protocol
                )
            )
            if (result is ConversationCreationResult.Success) {
                MonkeyConversation(self, result.conversation.id, isDestroyable, monkeyList)
            } else {
                if (result is ConversationCreationResult.UnknownFailure) {
                    val cause = result.cause
                    if (cause is MLSFailure.Generic) {
                        error("${self.monkeyType.userId()} could not create group $name: ${cause.rootCause}")
                    } else {
                        error("${self.monkeyType.userId()} could not create group $name: ${result.cause}")
                    }
                } else {
                    error("${self.monkeyType.userId()} could not create group $name: $result")
                }
            }
        }
    }

    override suspend fun leaveConversation(conversationId: ConversationId, creatorId: UserId) {
        if (this.monkeyType.userId() == creatorId) {
            error("Creator of the group can't leave")
        }
        this.monkeyState.readyThen {
            conversations.leaveConversation(conversationId)
        }
    }

    override suspend fun destroyConversation(conversationId: ConversationId, creatorId: UserId) {
        if (this.monkeyType.userId() != creatorId) {
            error("Only the creator can destroy a group")
        }

        this.monkeyState.readyThen {
            conversations.deleteTeamConversation
        }
    }

    override suspend fun addMonkeysToConversation(conversationId: ConversationId, monkeys: List<Monkey>) {
        this.monkeyState.readyThen {
            conversations.addMemberToConversationUseCase(conversationId, monkeys.map { it.monkeyType.userId() })
        }
    }

    override suspend fun removeMonkeyFromConversation(id: ConversationId, monkey: Monkey) {
        this.monkeyState.readyThen {
            conversations.removeMemberFromConversation(
                id, monkey.monkeyType.userId()
            )
        }
    }

    override suspend fun sendDirectMessageTo(anotherMonkey: Monkey, message: String) {
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

    override suspend fun sendMessageTo(conversationId: ConversationId, message: String) {
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
