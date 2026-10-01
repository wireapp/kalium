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
import com.wire.kalium.mocks.responses.conversation.ConversationResponseJson
import com.wire.kalium.network.api.authenticated.meeting.MeetingDTO
import com.wire.kalium.network.api.authenticated.meeting.UpsertMeetingResponse
import com.wire.kalium.network.api.authenticated.meeting.UpsertMeetingRequest
import com.wire.kalium.network.api.authenticated.meeting.toMeetingDTO
import com.wire.kalium.network.api.model.MeetingId
import com.wire.kalium.network.api.unbound.versioning.VersionInfoDTO
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.api.model.UserId
import com.wire.kalium.network.api.unbound.configuration.ApiVersionDTO
import com.wire.kalium.network.networkContainer.AuthenticatedHttpClientProvider
import com.wire.kalium.network.networkContainer.AuthenticatedNetworkContainer
import com.wire.kalium.network.networkContainer.UnauthenticatedNetworkClientProvider
import com.wire.kalium.network.networkContainer.UnauthenticatedNetworkContainer
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.headersOf
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import io.ktor.http.content.TextContent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Instant
import kotlin.test.assertIs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class V17NetworkContainerTest {
    private val config = TEST_BACKEND_CONFIG.copy(
        metaData = TEST_BACKEND_CONFIG.metaData.copy(commonApiVersion = ApiVersionDTO.Valid(17))
    )

    @Test
    fun authenticatedFactoryRetainsMeetingRequestsAndAuthentication() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/v17/meetings/list", request.url.encodedPath)
            assertEquals("Bearer ${TestSessionManagerV0.SESSION.accessToken}", request.headers[HttpHeaders.Authorization])
            respond("[]", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val container = authenticated(engine)
        try {
            val response = container.meetingApi.fetchMeetings()
            assertTrue(response.isSuccessful())
            assertEquals(emptyList(), response.value)
        } finally {
            container.close()
            engine.close()
            engine.coroutineContext.job.join()
        }
    }

    @Test
    fun unauthenticatedFactoryRetainsDomainLookupRequestsAndSerialization() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/v17/custom-backend/by-domain/wire.com", request.url.encodedPath)
            respond(
                """{"config_json_url":"https://wire.com/config","webapp_welcome_url":"https://wire.com/welcome"}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json")
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
    fun closeDrainsBothAuthenticatedClientsAndTheirOwnedEngine() = runTest {
        val container = authenticated()
        val main = container.cellsHttpClient
        val uncompressed = (container as AuthenticatedHttpClientProvider).networkClientWithoutCompression.httpClient
        try {
            assertNotSame(main, uncompressed)
            assertSame(main.engine, uncompressed.engine)
            assertSame(container.notificationApi, container.notificationApi)
        } finally {
            container.close()
        }
        assertTrue(main.coroutineContext.job.isCompleted)
        assertTrue(uncompressed.coroutineContext.job.isCompleted)
        assertTrue(main.engine.coroutineContext.job.isCompleted)
        container.close()
    }


    @Test
    fun createAndUpdateSendTzidAndDecodeV17WithoutTrial() = runTest {
        val meetingJson = Json.parseToJsonElement(MEETING_JSON).jsonObject
        val response = JsonObject((meetingJson - "trial") + mapOf(
            "tzid" to JsonPrimitive("Australia/Sydney"),
            "conversation" to Json.parseToJsonElement(ConversationResponseJson.v3.rawJson)
        )).toString()
        var calls = 0
        val engine = MockEngine { request ->
            assertEquals(if (calls++ == 0) HttpMethod.Post else HttpMethod.Put, request.method)
            assertEquals(if (calls == 1) "/v17/meetings" else "/v17/meetings/wire.com/meeting", request.url.encodedPath)
            val body = Json.parseToJsonElement(assertIs<TextContent>(request.body).text).jsonObject
            assertEquals("Australia/Sydney", body.getValue("tzid").jsonPrimitive.content)
            assertEquals(false, body.containsKey("trial"))
            respond(response, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val container = authenticated(engine)
        try {
            val request = UpsertMeetingRequest(
                startTime = Instant.parse("2026-10-01T08:00:00Z"), endTime = Instant.parse("2026-10-01T09:00:00Z"),
                title = "Meeting", recurrence = null, tzid = "Australia/Sydney"
            )
            val created = container.meetingApi.createNewMeeting(request)
            assertTrue(created.isSuccessful())
            assertEquals("Australia/Sydney", created.value.toMeetingDTO().tzid)
            val updated = container.meetingApi.updateMeeting(MeetingId("meeting", "wire.com"), request)
            assertTrue(updated.isSuccessful())
            assertEquals("Australia/Sydney", updated.value.tzid)
            assertEquals(2, calls)
        } finally {
            container.close()
            engine.close()
            engine.coroutineContext.job.join()
        }
    }

    @Test
    fun legacyMissingTzidUsesMigrationZoneAndExplicitUnknownIdsAreRetained() {
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        assertEquals("Europe/Berlin", json.decodeFromString<MeetingDTO>(MEETING_JSON).tzid)
        val fields = Json.parseToJsonElement(MEETING_JSON).jsonObject
        val legacyResponse = JsonObject(fields + mapOf(
            "conversation" to Json.parseToJsonElement(ConversationResponseJson.v3.rawJson)
        )).toString()
        assertEquals("Europe/Berlin", json.decodeFromString<UpsertMeetingResponse>(legacyResponse).toMeetingDTO().tzid)
        for (tzid in listOf("US/Eastern", "Unsupported/TZID", "")) {
            val payload = JsonObject(fields + mapOf("tzid" to JsonPrimitive(tzid))).toString()
            assertEquals(tzid, json.decodeFromString<MeetingDTO>(payload).tzid)
        }
    }

    @Test
    fun v16RejectsMeetingsWithoutSendingNetworkRequests() = runTest {
        val engine = MockEngine { error("v16 meetings must not reach the network") }
        val container = authenticated(engine, version = 16)
        try {
            assertIs<NetworkResponse.Error>(container.meetingApi.fetchMeetings())
        } finally {
            container.close()
            engine.close()
            engine.coroutineContext.job.join()
        }
    }

    @Test
    fun v17NegotiationRequiresDevelopmentApisEnabled() {
        val server = VersionInfoDTO(listOf(16, 17), "wire.com", false, listOf(15))
        assertEquals(ApiVersionDTO.Valid(15), BackendMetaDataUtilImpl.calculateApiVersion(server, developmentApiEnabled = false).commonApiVersion)
        assertEquals(ApiVersionDTO.Valid(17), BackendMetaDataUtilImpl.calculateApiVersion(server, developmentApiEnabled = true).commonApiVersion)
    }

    private fun authenticated(engine: HttpClientEngine? = null, version: Int = 17): AuthenticatedNetworkContainer {
        val manager = object : SessionManager by TestSessionManagerV0() {
            override fun serverConfig() = config.copy(metaData = config.metaData.copy(commonApiVersion = ApiVersionDTO.Valid(version)))
            override fun proxyCredentials() = null
        }
        return AuthenticatedNetworkContainer.create(
            manager,
            selfUserId = UserId("self", "wire.com"),
            userAgent = "test",
            certificatePinning = emptyMap(),
            mockEngine = engine,
            mockWebSocketSession = null,
            kaliumLogger = kaliumLogger,
        )
    }
}

private val MEETING_JSON = """
    {"qualified_id":{"id":"meeting","domain":"wire.com"},
     "qualified_conversation":{"id":"conversation","domain":"wire.com"},
     "qualified_creator":{"id":"creator","domain":"wire.com"},
     "created_at":"2026-09-30T08:00:00Z","updated_at":null,"title":"Meeting",
     "start_time":"2026-10-01T08:00:00Z","end_time":"2026-10-01T09:00:00Z","trial":false,"recurrence":null}
""".trimIndent()
