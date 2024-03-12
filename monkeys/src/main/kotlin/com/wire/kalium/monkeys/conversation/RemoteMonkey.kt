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
import com.wire.kalium.monkeys.logger
import com.wire.kalium.monkeys.model.ConversationDef
import com.wire.kalium.monkeys.model.MonkeyId
import com.wire.kalium.monkeys.model.UserCount
import com.wire.kalium.monkeys.pool.MonkeyConfig
import com.wire.kalium.monkeys.pool.MonkeyPool
import com.wire.kalium.monkeys.renderMonkeyTemplate
import com.wire.kalium.monkeys.runSysCommand
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
import com.wire.kalium.monkeys.server.routes.SET_MONKEY
import com.wire.kalium.monkeys.server.routes.WARM_UP
import com.wire.kalium.network.KaliumKtorCustomLogging
import com.wire.kalium.network.tools.KtxSerializer
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retry

private const val RETRY_COUNT = 3L

@Suppress("TooManyFunctions")
class RemoteMonkey(monkeyConfig: MonkeyConfig.Remote, monkeyType: MonkeyType, internalId: MonkeyId) :
    Monkey(monkeyType, internalId) {
    private val baseUrl: String

    init {
        baseUrl = monkeyConfig.addressResolver(monkeyType.userData(), internalId)
        logger.i("Starting monkey server for ${this.monkeyType.userId()}")
        monkeyConfig.startCommand.renderMonkeyTemplate(monkeyType.userData(), internalId).runSysCommand(monkeyConfig.wait)
        servers.add(baseUrl)
    }

    companion object {
        private val servers = mutableListOf<String>()
        private val httpClient by lazy {

            HttpClient(OkHttp.create()) {
                expectSuccess = true
                install(KaliumKtorCustomLogging)
                install(UserAgent) {
                    agent = "Wire Infinite Monkeys"
                }
                install(ContentNegotiation) {
                    json(KtxSerializer.json)
                }
            }
        }

        @Suppress("TooGenericExceptionCaught")
        suspend fun tearDown() = coroutineScope {
            servers.map {
                async {
                    try {
                        val code = httpClient.config { expectSuccess = false }.get("$it/shutdown").status
                        if (code != HttpStatusCode.Gone) {
                            error("Failed shutting down remote monkey")
                        }
                    } catch (e: Exception) {
                        logger.e("Failed stopping $it: $e")
                    }
                }
            }.awaitAll()
        }
    }

    private fun url(endpoint: String): Url {
        return Url("$baseUrl/$endpoint")
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend inline fun <reified T> get(endpoint: String): T {
        try {
            return flow<T> {
                emit(httpClient.get(url(endpoint)).body())
            }.retry(RETRY_COUNT).first()
        } catch (e: Exception) {
            logger.e("Error $endpoint: $e")
            throw e
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend inline fun <reified T, reified B> post(endpoint: String, body: B): T {
        try {
            return flow<T> {
                emit(httpClient.post(url(endpoint)) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }.body())
            }.retry(RETRY_COUNT).first()
        } catch (e: Exception) {
            logger.e("Error $endpoint: $e")
            throw e
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend inline fun <reified B> postNoBody(endpoint: String, body: B): HttpStatusCode {
        try {
            return flow {
                emit(httpClient.post(url(endpoint)) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }.status)
            }.retry(RETRY_COUNT).first()
        } catch (e: Exception) {
            logger.e("Error $endpoint: $e")
            throw e
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend inline fun post(endpoint: String) {
        try {
            flow {
                httpClient.post(url(endpoint))
                emit(Unit)
            }.retry(RETRY_COUNT).first()
        } catch (e: Exception) {
            logger.e("Error $endpoint: $e")
            throw e
        }
    }

    suspend fun setMonkey() {
        postNoBody(SET_MONKEY, monkeyType.userData().backendConfig())
    }

    override suspend fun isSessionActive(): Boolean {
        return get(IS_SESSION_ACTIVE)
    }

    override suspend fun login(coreLogic: CoreLogic, callback: (Monkey) -> Unit) {
        post(LOGIN)
        callback(this)
    }

    override suspend fun connectedMonkeys(): List<UserId> {
        return get(CONNECTED_MONKEYS)
    }

    override suspend fun logout(callback: (Monkey) -> Unit) {
        post(LOGOUT)
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

    override suspend fun pendingConnectionRequests(): List<UserId> {
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
        post(WARM_UP)
    }

    override suspend fun createConversation(
        name: String,
        monkeyList: List<Monkey>,
        protocol: ConversationOptions.Protocol,
        isDestroyable: Boolean
    ): MonkeyConversation {
        val result: ConversationId = post(
            CREATE_CONVERSATION, CreateConversationRequest(name, monkeyList.map { it.monkeyType.userId() }, protocol, isDestroyable)
        )
        return MonkeyConversation(this, result, isDestroyable, monkeyList)
    }

    override suspend fun leaveConversation(conversationId: ConversationId, creatorId: UserId) {
        postNoBody(LEAVE_CONVERSATION, conversationId)
    }

    override suspend fun destroyConversation(conversationId: ConversationId, creatorId: UserId) {
        postNoBody(DESTROY_CONVERSATION, conversationId)
    }

    override suspend fun addMonkeysToConversation(conversationId: ConversationId, monkeys: List<Monkey>) {
        postNoBody(ADD_MONKEY_TO_CONVERSATION, AddMonkeysRequest(conversationId, monkeys.map { it.monkeyType.userId() }))
    }

    override suspend fun removeMonkeyFromConversation(id: ConversationId, monkey: Monkey) {
        postNoBody(REMOVE_MONKEY_FROM_CONVERSATION, RemoveMonkeyRequest(id, monkey.monkeyType.userId()))
    }

    override suspend fun sendDirectMessageTo(anotherMonkey: Monkey, message: String) {
        postNoBody(SEND_DM, SendDMRequest(anotherMonkey.monkeyType.userId(), message))
    }

    override suspend fun sendMessageTo(conversationId: ConversationId, message: String) {
        postNoBody(SEND_MESSAGE, SendMessageRequest(conversationId, message))
    }
}
