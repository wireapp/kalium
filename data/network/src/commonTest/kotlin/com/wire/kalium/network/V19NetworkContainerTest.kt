/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.network

import com.wire.kalium.api.TEST_BACKEND_CONFIG
import com.wire.kalium.api.TestSessionManagerV0
import com.wire.kalium.mocks.responses.conversation.ConversationResponseJson
import com.wire.kalium.network.api.authenticated.meeting.CreateMeetingRequest
import com.wire.kalium.network.api.authenticated.meeting.MeetingTypeDTO
import com.wire.kalium.network.api.authenticated.meeting.UpdateMeetingRequest
import com.wire.kalium.network.api.model.MeetingId
import com.wire.kalium.network.api.model.UserId
import com.wire.kalium.network.api.unbound.configuration.ApiVersionDTO
import com.wire.kalium.network.api.unbound.versioning.VersionInfoDTO
import com.wire.kalium.network.networkContainer.AuthenticatedNetworkContainer
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

class V19NetworkContainerTest {
    @Test
    fun createAndUpdateUseV19MeetingPayloads() = runTest {
        val start = Instant.parse("2026-10-01T08:00:00Z")
        val end = Instant.parse("2026-10-01T09:00:00Z")
        val response = JsonObject(
            Json.parseToJsonElement(MEETING_JSON).jsonObject + mapOf(
                "conversation" to Json.parseToJsonElement(ConversationResponseJson.v3.rawJson),
                "type" to JsonPrimitive("immediate"),
                "link" to JsonPrimitive("https://meet.example/meeting"),
                "has_password" to JsonPrimitive(true),
            )
        ).toString()
        var calls = 0
        val engine = MockEngine { request ->
            val body = Json.parseToJsonElement(assertIs<TextContent>(request.body).text).jsonObject
            assertEquals("Bearer ${TestSessionManagerV0.SESSION.accessToken}", request.headers[HttpHeaders.Authorization])
            assertEquals("immediate", body.getValue("type").jsonPrimitive.content)
            assertEquals("Europe/Warsaw", body.getValue("tzid").jsonPrimitive.content)
            when (calls++) {
                0 -> {
                    assertEquals(HttpMethod.Post, request.method)
                    assertEquals("/v19/meetings", request.url.encodedPath)
                    assertEquals("secret", body.getValue("password").jsonPrimitive.content)
                }
                else -> {
                    assertEquals(HttpMethod.Put, request.method)
                    assertEquals("/v19/meetings/wire.com/meeting", request.url.encodedPath)
                    assertFalse(body.containsKey("password"))
                }
            }
            respond(response, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val manager = object : SessionManager by TestSessionManagerV0() {
            override fun serverConfig() = TEST_BACKEND_CONFIG.copy(
                metaData = TEST_BACKEND_CONFIG.metaData.copy(commonApiVersion = ApiVersionDTO.Valid(19))
            )
            override fun proxyCredentials() = null
        }
        val container = AuthenticatedNetworkContainer.create(
            sessionManager = manager,
            selfUserId = UserId("self", "wire.com"),
            userAgent = "test",
            certificatePinning = emptyMap(),
            mockEngine = engine,
            mockWebSocketSession = null,
            kaliumLogger = kaliumLogger,
        )
        try {
            val created = container.meetingApi.createNewMeeting(
                CreateMeetingRequest(start, end, "Europe/Warsaw", "Meeting", null, MeetingTypeDTO.IMMEDIATE, "secret")
            )
            assertTrue(created.isSuccessful())
            assertEquals("https://meet.example/meeting", created.value.link)
            assertTrue(created.value.hasPassword)
            val updated = container.meetingApi.updateMeeting(
                MeetingId("meeting", "wire.com"),
                UpdateMeetingRequest(start, end, "Europe/Warsaw", "Meeting", null, MeetingTypeDTO.IMMEDIATE)
            )
            assertTrue(updated.isSuccessful())
            assertEquals(2, calls)
        } finally {
            container.close()
            engine.close()
            engine.coroutineContext.job.join()
        }
    }

    @Test
    fun v19NegotiationRequiresDevelopmentApisEnabled() {
        val server = VersionInfoDTO(listOf(19), "wire.com", false, listOf(18))
        assertEquals(
            ApiVersionDTO.Valid(18),
            BackendMetaDataUtilImpl.calculateApiVersion(server, developmentApiEnabled = false).commonApiVersion
        )
        assertEquals(
            ApiVersionDTO.Valid(19),
            BackendMetaDataUtilImpl.calculateApiVersion(server, developmentApiEnabled = true).commonApiVersion
        )
    }
}

private val MEETING_JSON = """
    {"qualified_id":{"id":"meeting","domain":"wire.com"},
     "qualified_conversation":{"id":"conversation","domain":"wire.com"},
     "qualified_creator":{"id":"creator","domain":"wire.com"},
     "created_at":"2026-09-30T08:00:00Z","updated_at":null,"title":"Meeting",
     "start_time":"2026-10-01T08:00:00Z","end_time":"2026-10-01T09:00:00Z", "recurrence":null}
""".trimIndent()
