/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.network

import com.wire.kalium.api.TEST_BACKEND_CONFIG
import com.wire.kalium.api.TestSessionManagerV0
import com.wire.kalium.network.api.authenticated.conversation.AddConversationMembersRequest
import com.wire.kalium.network.api.model.ConversationId
import com.wire.kalium.network.api.model.UserId
import com.wire.kalium.network.api.unbound.configuration.ApiVersionDTO
import com.wire.kalium.network.api.unbound.versioning.VersionInfoDTO
import com.wire.kalium.network.exceptions.APINotSupported
import com.wire.kalium.network.exceptions.AdminlessConversationError
import com.wire.kalium.network.networkContainer.AuthenticatedHttpClientProvider
import com.wire.kalium.network.networkContainer.AuthenticatedNetworkContainer
import com.wire.kalium.network.networkContainer.UnauthenticatedNetworkClientProvider
import com.wire.kalium.network.networkContainer.UnauthenticatedNetworkContainer
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class V18NetworkContainerTest {
    private val config = TEST_BACKEND_CONFIG.copy(
        metaData = BackendMetaDataUtilImpl.calculateApiVersion(
            VersionInfoDTO(null, "wire.com", false, listOf(15, 16, 17, 18)),
            developmentApiEnabled = false
        )
    )
    private val conversationId = ConversationId("conversation", "wire.com")
    private val member = UserId("member", "wire.com")
    private val replaceRequest = AddConversationMembersRequest(listOf(member), "wire_member")
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun replaceMembersSendsQualifiedMembersAndRoleWithV18Authentication() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Put, request.method)
            assertEquals("/v18/conversations/wire.com/conversation/members", request.url.encodedPath)
            assertEquals("Bearer ${TestSessionManagerV0.SESSION.accessToken}", request.headers[HttpHeaders.Authorization])
            val expected = Json.parseToJsonElement(
                """{"qualified_users":[{"id":"member","domain":"wire.com"}],"conversation_role":"wire_member"}"""
            )
            assertEquals(expected, Json.parseToJsonElement(assertIs<TextContent>(request.body).text))
            respond("", HttpStatusCode.NoContent)
        }
        withAuthenticated(engine) { container ->
            val response = container.conversationApi.replaceMembers(conversationId, replaceRequest)
            assertTrue(response.isSuccessful())
            assertEquals(Unit, response.value)
        }
    }

    @Test
    fun replaceMembersPreservesEligibleMembersFromAdminlessError() = runTest {
        val engine = MockEngine { respond(ADMINLESS_ERROR, HttpStatusCode.Forbidden, jsonHeaders) }
        withAuthenticated(engine) { container ->
            val response = assertIs<NetworkResponse.Error>(container.conversationApi.replaceMembers(conversationId, replaceRequest))
            val exception = assertIs<AdminlessConversationError>(response.kException)
            assertEquals(403, exception.errorResponse.code)
            assertEquals("adminless-conversation", exception.errorResponse.label)
            assertEquals(listOf(member), exception.errorResponse.eligibleMembers)
        }
    }

    @Test
    fun replaceMembersKeepsOtherErrorsOnTheGenericErrorPath() = runTest {
        val responses = listOf(
            HttpStatusCode.Forbidden to ADMINLESS_ERROR.replace("adminless-conversation", "operation-denied"),
            HttpStatusCode.BadRequest to ADMINLESS_ERROR.replace("403", "400"),
            HttpStatusCode.Forbidden to "invalid-json",
        )
        for ((status, body) in responses) {
            val engine = MockEngine { respond(body, status, jsonHeaders) }
            withAuthenticated(engine) { container ->
                val response = assertIs<NetworkResponse.Error>(container.conversationApi.replaceMembers(conversationId, replaceRequest))
                assertFalse(response.kException is AdminlessConversationError)
            }
        }
    }

    @Test
    fun olderApisRejectReplaceMembersWithoutSendingARequest() = runTest {
        for (version in 0..17) {
            val engine = MockEngine { error("API $version must reject replaceMembers locally") }
            withAuthenticated(engine, version) { container ->
                val response = assertIs<NetworkResponse.Error>(container.conversationApi.replaceMembers(conversationId, replaceRequest))
                val exception = assertIs<APINotSupported>(response.kException)
                assertTrue(exception.errorBody.contains("API V18"))
            }
        }
    }

    @Test
    fun v18NegotiationDoesNotRequireDevelopmentApisEnabled() {
        for (server in listOf(
            VersionInfoDTO(null, "wire.com", false, listOf(18)),
            VersionInfoDTO(emptyList(), "wire.com", false, listOf(15, 18)),
        )) {
            for (developmentApiEnabled in listOf(false, true)) {
                assertEquals(
                    ApiVersionDTO.Valid(18),
                    BackendMetaDataUtilImpl.calculateApiVersion(server, developmentApiEnabled = developmentApiEnabled).commonApiVersion
                )
            }
        }
    }

    @Test
    fun serverDevelopmentApisStillRequireDevelopmentApisEnabled() {
        val server = VersionInfoDTO(listOf(16, 17, 18), "wire.com", false, listOf(15))
        assertEquals(
            ApiVersionDTO.Valid(15),
            BackendMetaDataUtilImpl.calculateApiVersion(server, developmentApiEnabled = false).commonApiVersion
        )
        assertEquals(
            ApiVersionDTO.Valid(18),
            BackendMetaDataUtilImpl.calculateApiVersion(server, developmentApiEnabled = true).commonApiVersion
        )
    }

    @Test
    fun unauthenticatedFactoryRetainsV18DomainLookupAndSerialization() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/v18/custom-backend/by-domain/wire.com", request.url.encodedPath)
            respond(
                """{"config_json_url":"https://wire.com/config","webapp_welcome_url":"https://wire.com/welcome"}""",
                headers = jsonHeaders
            )
        }
        val container = UnauthenticatedNetworkContainer.create(config, null, "test", false, emptyMap(), engine)
        val client = (container as UnauthenticatedNetworkClientProvider).unauthenticatedNetworkClient.httpClient
        try {
            val response = container.domainLookupApi.lookup("wire.com")
            assertTrue(response.isSuccessful())
            assertEquals("https://wire.com/config", response.value.configJsonUrl)
            assertEquals("https://wire.com/welcome", response.value.webappWelcomeUrl)
        } finally {
            client.close()
            engine.close()
            engine.coroutineContext.job.join()
        }
    }

    @Test
    fun v18RetainsInheritedMeetingRequests() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/v18/meetings/list", request.url.encodedPath)
            respond("[]", headers = jsonHeaders)
        }
        withAuthenticated(engine) { container ->
            val response = container.meetingApi.fetchMeetings()
            assertTrue(response.isSuccessful())
            assertEquals(emptyList(), response.value)
        }
    }

    @Test
    fun closeDrainsBothClientsAndTheirOwnedEngine() = runTest {
        withAuthenticated { container ->
            val main = container.cellsHttpClient
            val uncompressed = (container as AuthenticatedHttpClientProvider).networkClientWithoutCompression.httpClient
            assertNotSame(main, uncompressed)
            assertSame(main.engine, uncompressed.engine)
            assertSame(container.notificationApi, container.notificationApi)
            container.close()
            assertTrue(main.coroutineContext.job.isCompleted)
            assertTrue(uncompressed.coroutineContext.job.isCompleted)
            assertTrue(main.engine.coroutineContext.job.isCompleted)
            container.close()
        }
    }

    @Test
    fun closeRetainsAnExternallyOwnedEngine() = runTest {
        val engine = MockEngine { error("No network request expected") }
        withAuthenticated(engine) { container ->
            val client = container.cellsHttpClient
            container.close()
            assertTrue(client.coroutineContext.job.isCompleted)
            assertFalse(engine.coroutineContext.job.isCompleted)
        }
    }

    private suspend fun withAuthenticated(
        engine: HttpClientEngine? = null,
        version: Int = config.metaData.commonApiVersion.version,
        block: suspend (AuthenticatedNetworkContainer) -> Unit,
    ) {
        val manager = object : SessionManager by TestSessionManagerV0() {
            override fun serverConfig() = config.copy(metaData = config.metaData.copy(commonApiVersion = ApiVersionDTO.Valid(version)))
            override fun proxyCredentials() = null
        }
        val container = AuthenticatedNetworkContainer.create(
            manager,
            selfUserId = UserId("self", "wire.com"),
            userAgent = "test",
            certificatePinning = emptyMap(),
            mockEngine = engine,
            mockWebSocketSession = null,
            kaliumLogger = kaliumLogger,
        )
        try {
            block(container)
        } finally {
            container.close()
            engine?.close()
            engine?.coroutineContext?.job?.join()
        }
    }

    private companion object {
        val ADMINLESS_ERROR = """
            {"code":403,"label":"adminless-conversation","message":"The conversation would be left without an admin",
             "eligible_members":[{"id":"member","domain":"wire.com"}]}
        """.trimIndent()
    }
}
