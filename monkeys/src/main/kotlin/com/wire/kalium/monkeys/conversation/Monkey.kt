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
import com.wire.kalium.logic.data.conversation.ConversationOptions
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.monkeys.model.ConversationDef
import com.wire.kalium.monkeys.model.MonkeyId
import com.wire.kalium.monkeys.model.UserCount
import com.wire.kalium.monkeys.model.UserData
import com.wire.kalium.monkeys.pool.MonkeyConfig
import com.wire.kalium.monkeys.pool.MonkeyPool
import com.wire.kalium.monkeys.pool.resolveUserCount

sealed class MonkeyType {
    data class Internal(val user: UserData) : MonkeyType()
    data class External(val userId: UserId) : MonkeyType()
    data class Remote(val user: UserData) : MonkeyType()

    fun userId(): UserId = when (this) {
        is External -> this.userId
        is Internal -> this.user.userId
        is Remote -> this.user.userId
    }

    /**
     * Ensures that the monkey type is internal or remote and return its user data
     */
    fun userData(): UserData = when (this) {
        is External -> error("This is an external Monkey and can't perform this operation")
        is Internal -> this.user
        is Remote -> this.user
    }
}

@Suppress("TooManyFunctions")
abstract class Monkey(val monkeyType: MonkeyType, val internalId: MonkeyId) {
    companion object {
        // this means there are users within the team not managed by IM
        // We can still send messages and add them to groups but not act on their behalf
        // MonkeyId is irrelevant for external users as we will never be able to act on their behalf
        fun external(userId: UserId) = LocalMonkey(MonkeyType.External(userId), MonkeyId(-1, "", -1))
        fun internal(user: UserData, monkeyId: MonkeyId) = LocalMonkey(MonkeyType.Internal(user), monkeyId)
        fun remote(monkeyConfig: MonkeyConfig.Remote, user: UserData, monkeyId: MonkeyId) =
            RemoteMonkey(monkeyConfig, MonkeyType.Remote(user), monkeyId)
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

    abstract suspend fun isSessionActive(): Boolean

    /**
     * Logs user in and register client (if not registered)
     */
    abstract suspend fun login(coreLogic: CoreLogic, callback: (Monkey) -> Unit)

    abstract suspend fun connectedMonkeys(): List<UserId>

    abstract suspend fun logout(callback: (Monkey) -> Unit)

    suspend fun randomPeer(monkeyPool: MonkeyPool): Monkey {
        return monkeyPool.get(this.connectedMonkeys().randomOrNull() ?: error("Monkey ${this.monkeyType.userId()} not connected to anyone"))
    }

    suspend fun randomPeers(userCount: UserCount, monkeyPool: MonkeyPool, filterOut: List<UserId> = listOf()): List<Monkey> {
        val connectedMonkeys = this.connectedMonkeys()
        val count = resolveUserCount(userCount, connectedMonkeys.count().toUInt())
        return connectedMonkeys.filterNot { filterOut.contains(it) }.shuffled().map(monkeyPool::get).take(count.toInt())
    }

    abstract suspend fun sendRequest(anotherMonkey: Monkey)

    abstract suspend fun acceptRequest(anotherMonkey: Monkey)

    abstract suspend fun rejectRequest(anotherMonkey: Monkey)

    abstract suspend fun pendingConnectionRequests(): List<UserId>

    abstract suspend fun createConversation(
        name: String,
        monkeyList: List<Monkey>,
        protocol: ConversationOptions.Protocol,
        isDestroyable: Boolean = true
    ): MonkeyConversation

    abstract suspend fun leaveConversation(conversationId: ConversationId)

    abstract suspend fun destroyConversation(conversationId: ConversationId)

    abstract suspend fun addMonkeysToConversation(conversationId: ConversationId, monkeys: List<Monkey>)

    abstract suspend fun removeMonkeyFromConversation(id: ConversationId, monkey: Monkey)

    abstract suspend fun sendDirectMessageTo(anotherMonkey: Monkey, message: String)

    abstract suspend fun sendMessageTo(conversationId: ConversationId, message: String)

<<<<<<< HEAD
    @Suppress("LongParameterList")
    abstract suspend fun createPrefixedConversation(
        name: String,
        protocol: ConversationOptions.Protocol,
        userCount: UserCount,
        coreLogic: CoreLogic,
        monkeyPool: MonkeyPool,
        preset: ConversationDef?
    ): MonkeyConversation

    abstract suspend fun warmUp(core: CoreLogic)
=======
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
>>>>>>> c73f595497 (fix: Change return type of getConversationByGroupID to nullable for epoch changes (#2585))
}
