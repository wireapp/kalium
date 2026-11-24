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
package com.wire.kalium.api.v17

import com.wire.kalium.api.ApiTest
import com.wire.kalium.api.TEST_BACKEND_CONFIG
import com.wire.kalium.api.TestSessionManagerV0
import com.wire.kalium.mocks.responses.ListUsersResponseJson
import com.wire.kalium.network.DevelopmentApiVersions
import com.wire.kalium.network.SupportedApiVersions
import com.wire.kalium.network.api.authenticated.userDetails.ListUserRequest
import com.wire.kalium.network.api.authenticated.userDetails.qualifiedIds
import com.wire.kalium.network.api.model.ContactStatusDTO
import com.wire.kalium.network.api.model.UserProfileDTO
import com.wire.kalium.network.api.unbound.configuration.ApiVersionDTO
import com.wire.kalium.network.api.v16.authenticated.UserDetailsApiV16
import com.wire.kalium.network.api.v17.authenticated.TeamsApiV17
import com.wire.kalium.network.api.v17.authenticated.UserDetailsApiV17
import com.wire.kalium.network.api.v18.authenticated.UserDetailsApiV18
import com.wire.kalium.network.kaliumLogger
import com.wire.kalium.network.networkContainer.AuthenticatedNetworkContainer
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.network.tools.KtxSerializer
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class ContactStatusApiTest : ApiTest() {
    private val profile = ListUsersResponseJson.v4.serializableData.usersFound.first()

    @Test
    fun factoryPropagatesFlagOnlyToBulkUserRequestsForEverySupportedVersionFromV17() = runTest {
        // Include the pre-feature boundary and automatically cover future production/development versions.
        val versions = (setOf(16, 17, 18, 19) + SupportedApiVersions + DevelopmentApiVersions).filter { it >= 16 }.sorted()
        for ((version, enabled) in versions.flatMap { version -> listOf(false, true).map { version to it } }) {
            val config = TEST_BACKEND_CONFIG.copy(
                metaData = TEST_BACKEND_CONFIG.metaData.copy(commonApiVersion = ApiVersionDTO.Valid(version))
            )
            val manager = object : SessionManager by TestSessionManagerV0() {
                override fun serverConfig() = config
                override fun proxyCredentials() = null
            }
            val engine = MockEngine { request ->
                val expected = if (request.method == HttpMethod.Post && enabled && version >= 17) "true" else null
                assertEquals(expected, request.url.parameters["include-contact-status"], "API v$version, enabled=$enabled")
                val body = if (request.method == HttpMethod.Get) KtxSerializer.json.encodeToString(profile) else ListUsersResponseJson.v4.rawJson
                respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }
            val container = AuthenticatedNetworkContainer.create(
                sessionManager = manager, selfUserId = profile.id, userAgent = "test", certificatePinning = emptyMap(),
                mockEngine = engine, mockWebSocketSession = null, kaliumLogger = kaliumLogger,
                pendingConnectionsEnabled = enabled,
            )
            try {
                assertTrue(container.userDetailsApi.getUserInfo(profile.id).isSuccessful())
                assertTrue(container.userDetailsApi.getMultipleUsers(ListUserRequest.qualifiedIds(listOf(profile.id))).isSuccessful())
            } finally {
                container.close()
                engine.close()
                engine.coroutineContext.job.join()
            }
        }
    }

    @Test
    fun bulkProfilesRequestContactStatusOnlyFromV17() = runTest {
        for ((version, enabled) in listOf(16, 17, 18).flatMap { version -> listOf(false, true).map { version to it } }) {
            val params = ListUserRequest.qualifiedIds(listOf(profile.id))
            val client = mockAuthenticatedNetworkClient(
                ListUsersResponseJson.v4.rawJson,
                HttpStatusCode.OK,
                assertion = {
                    assertPost()
                    assertPathEqual("/list-users")
                    assertJsonBodyContent(KtxSerializer.json.encodeToString(params))
                    if (version >= 17 && enabled) assertQueryParameter("include-contact-status", "true") else assertNoQueryParams()
                }
            )
            val api = when (version) {
                16 -> UserDetailsApiV16(client)
                17 -> UserDetailsApiV17(client, enabled)
                else -> UserDetailsApiV18(client, enabled)
            }
            assertTrue(api.getMultipleUsers(params).isSuccessful())
        }
    }

    @Test
    fun singleProfilesNeverRequestContactStatus() = runTest {
        for ((version, enabled) in listOf(16, 17, 18).flatMap { version -> listOf(false, true).map { version to it } }) {
            val client = mockAuthenticatedNetworkClient(
                KtxSerializer.json.encodeToString(profile),
                HttpStatusCode.OK,
                assertion = {
                    assertGet()
                    assertPathEqual("/users/${profile.id.domain}/${profile.id.value}")
                    assertNoQueryParams()
                }
            )
            val api = when (version) {
                16 -> UserDetailsApiV16(client)
                17 -> UserDetailsApiV17(client, enabled)
                else -> UserDetailsApiV18(client, enabled)
            }
            assertTrue(api.getUserInfo(profile.id).isSuccessful())
        }
    }

    @Test
    fun optionalAndFutureContactStatesDecodeWithoutFailingProfiles() {
        val base = KtxSerializer.json.parseToJsonElement(KtxSerializer.json.encodeToString(profile)).jsonObject
        for (state in listOf("contactable", "non-contactable", "future-state")) {
            val json = JsonObject(base + ("contact_status" to JsonObject(mapOf("state" to JsonPrimitive(state)))))
            val decoded = KtxSerializer.json.decodeFromString<UserProfileDTO>(json.toString())
            assertEquals(state, decoded.contactStatus?.state)
        }
        for (json in listOf(JsonObject(base - "contact_status"), JsonObject(base + ("contact_status" to JsonNull)))) {
            assertNull(KtxSerializer.json.decodeFromString<UserProfileDTO>(json.toString()).contactStatus)
        }
    }

    @Test
    fun singleUserAndTeamAppsResponsesCarryContactStatus() = runTest {
        val pending = profile.copy(contactStatus = ContactStatusDTO("non-contactable"))
        val userClient = mockAuthenticatedNetworkClient(KtxSerializer.json.encodeToString(pending), HttpStatusCode.OK)
        val user = UserDetailsApiV17(userClient).getUserInfo(profile.id)
        assertTrue(user.isSuccessful())
        assertEquals(pending.contactStatus, user.value.contactStatus)
        val appsClient = mockAuthenticatedNetworkClient(KtxSerializer.json.encodeToString(listOf(pending)), HttpStatusCode.OK)
        val apps = TeamsApiV17(appsClient).getTeamApps("team")
        assertTrue(apps.isSuccessful())
        assertEquals(pending.contactStatus, apps.value.single().contactStatus)
    }
}
