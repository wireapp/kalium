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
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationOptions
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.monkeys.model.ConversationDef
import com.wire.kalium.monkeys.model.MonkeyId
import com.wire.kalium.monkeys.model.UserCount
import com.wire.kalium.monkeys.pool.MonkeyPool
import com.wire.kalium.monkeys.server.model.AddMonkeysRequest
import com.wire.kalium.monkeys.server.model.CreateConversationRequest
import com.wire.kalium.monkeys.server.model.RemoveMonkeyRequest
import com.wire.kalium.monkeys.server.model.SendDMRequest
import com.wire.kalium.monkeys.server.model.SendMessageRequest
import com.wire.kalium.monkeys.server.routes.ACCEPT_REQUEST
import com.wire.kalium.monkeys.server.routes.ADD_MONKEY_TO_CONVERSATION
import com.wire.kalium.monkeys.server.routes.CONNECTED_MONKEYS
import com.wire.kalium.monkeys.server.routes.CREATE_CONVERSATION
import com.wire.kalium.monkeys.server.routes.DESTROY_CONVERSATION
import com.wire.kalium.monkeys.server.routes.IS_SESSION_ACTIVE
import com.wire.kalium.monkeys.server.routes.LEAVE_CONVERSATION
import com.wire.kalium.monkeys.server.routes.LOGIN
import com.wire.kalium.monkeys.server.routes.LOGOUT
import com.wire.kalium.monkeys.server.routes.PENDING_CONNECTIONS
import com.wire.kalium.monkeys.server.routes.REJECT_REQUEST
import com.wire.kalium.monkeys.server.routes.REMOVE_MONKEY_FROM_CONVERSATION
import com.wire.kalium.monkeys.server.routes.SEND_DM
import com.wire.kalium.monkeys.server.routes.SEND_MESSAGE
import com.wire.kalium.monkeys.server.routes.SEND_REQUEST
import com.wire.kalium.monkeys.server.routes.WARM_UP
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Url
import io.ktor.http.contentType

@Suppress("TooManyFunctions")
class RemoteMonkey(private val httpClient: HttpClient, private val baseUrl: String, monkeyType: MonkeyType, internalId: MonkeyId) :
    Monkey(monkeyType, internalId) {
    private fun url(endpoint: String): Url {
        return Url("$baseUrl/$endpoint")
    }

    private suspend inline fun <reified T> get(endpoint: String): T {
        return httpClient.get(url(endpoint)).body()
    }

    private suspend inline fun <reified T, reified B> post(endpoint: String, body: B): T {
        return httpClient.post(url(endpoint)) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()
    }

    private suspend inline fun emptyPost(endpoint: String) {
        httpClient.post(url(endpoint))
    }

    override suspend fun isSessionActive(): Boolean {
        return get(IS_SESSION_ACTIVE)
    }

    override suspend fun login(coreLogic: CoreLogic, callback: (Monkey) -> Unit) {
        emptyPost(LOGIN)
        callback(this)
    }

    override suspend fun connectedMonkeys(): List<UserId> {
        return get(CONNECTED_MONKEYS)
    }

    override suspend fun logout(callback: (Monkey) -> Unit) {
        emptyPost(LOGOUT)
        callback(this)
    }

    override suspend fun sendRequest(anotherMonkey: Monkey) {
        return post(SEND_REQUEST, anotherMonkey.monkeyType.userId())
    }

    override suspend fun acceptRequest(anotherMonkey: Monkey) {
        return post(ACCEPT_REQUEST, anotherMonkey.monkeyType.userId())
    }

    override suspend fun rejectRequest(anotherMonkey: Monkey) {
        return post(REJECT_REQUEST, anotherMonkey.monkeyType.userId())
    }

    override suspend fun pendingConnectionRequests(): List<ConversationDetails.Connection> {
        return get(PENDING_CONNECTIONS)
    }

    override suspend fun createPrefixedConversation(
        name: String,
        protocol: ConversationOptions.Protocol,
        userCount: UserCount,
        coreLogic: CoreLogic,
        monkeyPool: MonkeyPool,
        preset: ConversationDef?
    ): MonkeyConversation {
        val participants =
            preset?.initialMembers?.map { monkeyPool.getFromTeam(it.team, it.index) } ?: this.randomPeers(userCount, monkeyPool)
        return createConversation(name, participants, protocol, false)
    }

    override suspend fun warmUp(core: CoreLogic) {
        emptyPost(WARM_UP)
    }

    override suspend fun createConversation(
        name: String,
        monkeyList: List<Monkey>,
        protocol: ConversationOptions.Protocol,
        isDestroyable: Boolean
    ): MonkeyConversation {
        return post(
            CREATE_CONVERSATION,
            CreateConversationRequest(name, monkeyList.map { it.monkeyType.userId() }, protocol, isDestroyable)
        )
    }

    override suspend fun leaveConversation(conversationId: ConversationId) {
        return post(LEAVE_CONVERSATION, conversationId)
    }

    override suspend fun destroyConversation(conversationId: ConversationId) {
        return post(DESTROY_CONVERSATION, conversationId)
    }

    override suspend fun addMonkeysToConversation(conversationId: ConversationId, monkeys: List<Monkey>) {
        return post(ADD_MONKEY_TO_CONVERSATION, AddMonkeysRequest(conversationId, monkeys.map { it.monkeyType.userId() }))
    }

    override suspend fun removeMonkeyFromConversation(id: ConversationId, monkey: Monkey) {
        return post(REMOVE_MONKEY_FROM_CONVERSATION, RemoveMonkeyRequest(id, monkey.monkeyType.userId()))
    }

    override suspend fun sendDirectMessageTo(anotherMonkey: Monkey, message: String) {
        return post(SEND_DM, SendDMRequest(anotherMonkey.monkeyType.userId(), message))
    }

    override suspend fun sendMessageTo(conversationId: ConversationId, message: String) {
        return post(SEND_MESSAGE, SendMessageRequest(conversationId, message))
    }
}
