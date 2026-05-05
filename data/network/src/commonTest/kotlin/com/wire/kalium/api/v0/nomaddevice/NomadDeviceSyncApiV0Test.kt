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

package com.wire.kalium.api.v0.nomaddevice

import com.wire.kalium.api.ApiTest
import com.wire.kalium.api.TEST_BACKEND_CONFIG
import com.wire.kalium.api.json.model.testCredentials
import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.authenticated.nomaddevice.Conversation
import com.wire.kalium.network.api.authenticated.nomaddevice.ConversationMetadataEntry
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadBatchRestoreRequest
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadMessageEvent
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadMessageEventsRequest
import com.wire.kalium.network.api.base.authenticated.AccessTokenApi
import com.wire.kalium.network.api.base.authenticated.nomaddevice.NomadDeviceSyncApi
import com.wire.kalium.network.api.model.ProxyCredentialsDTO
import com.wire.kalium.network.api.model.SessionDTO
import com.wire.kalium.network.api.unbound.configuration.ServerConfigDTO
import com.wire.kalium.network.api.v0.authenticated.NomadDeviceSyncApiV0
import com.wire.kalium.network.api.v0.authenticated.networkContainer.AuthenticatedNetworkContainerV0
import com.wire.kalium.network.exceptions.APINotSupported
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isNoCryptoState
import com.wire.kalium.network.exceptions.isUserNotFound
import com.wire.kalium.network.kaliumLogger
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

internal class NomadDeviceSyncApiV0Test : ApiTest() {

    @Test
    fun givenNomadEvents_whenPosting_thenRequestShouldMatchContract() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            responseBody = "",
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertPost()
                assertJson()
                assertPathEqual("/event/messages")
                assertJsonBodyContent(EXPECTED_REQUEST_JSON)
            }
        )

        val api: NomadDeviceSyncApi = NomadDeviceSyncApiV0(networkClient, nomadServiceUrl = "https://nomad.example.com")
        val response = api.postMessageEvents(REQUEST)

        assertTrue(response.isSuccessful())
    }

    @Test
    fun givenNomadMessages_whenGettingAllMessages_thenRequestAndResponseShouldMatchContract() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            responseBody = ALL_MESSAGES_RESPONSE_JSON,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertGet()
                assertPathEqual("/event/messages")
            }
        )

        val api: NomadDeviceSyncApi = NomadDeviceSyncApiV0(networkClient, nomadServiceUrl = "https://nomad.example.com")
        val response = api.getAllMessages()

        assertTrue(response.isSuccessful())
        assertEquals(2, response.value.conversations.size)
        val firstMessage = response.value.conversations.first().messages.first()
        assertEquals(REACTION_RAW, firstMessage.reaction)
        assertEquals(READ_RECEIPT_RAW, firstMessage.readReceipt)
    }

    @Test
    fun givenNomadMessages_whenSyncingAllMessages_thenRequestIncludesSyncPathAndLimit() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            responseBody = ALL_MESSAGES_RESPONSE_JSON,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertGet()
                assertPathEqual("/event/messages/sync")
                assertQueryParameter("limit", "250")
            }
        )

        val api: NomadDeviceSyncApi = NomadDeviceSyncApiV0(networkClient, nomadServiceUrl = "https://nomad.example.com")
        val response = api.syncAllMessages(limit = 250)

        assertTrue(response.isSuccessful())
        assertEquals(2, response.value.conversations.size)
    }

    @Test
    fun givenNomadMessages_whenRestoringBatch_thenRequestMatchesContract() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            responseBody = BATCH_RESTORE_RESPONSE_JSON,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertGet()
                assertPathEqual("/event/messages/batch/restore")
                assertQueryParameter("conversation_ids", "conv123,conv456")
                assertQueryParameter("limit", "50")
                assertQueryParameter("before_timestamp", "1710936000")
                assertQueryParameter("next_cursor", "0")
            }
        )

        val api: NomadDeviceSyncApi = NomadDeviceSyncApiV0(networkClient, nomadServiceUrl = "https://nomad.example.com")
        val response = api.restoreMessagesBatch(
            NomadBatchRestoreRequest(
                conversationIds = listOf("conv123", "conv456"),
                limit = 50,
                beforeTimestamp = 1_710_936_000,
                nextCursor = 0
            )
        )

        assertTrue(response.isSuccessful())
        assertEquals(2, response.value.conversations.size)
        assertTrue(response.value.conversations.first().hasMore)
    }

    @Test
    fun givenConversationMetadata_whenGettingConversationMetadata_thenRequestAndResponseShouldMatchContract() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            responseBody = CONVERSATION_METADATA_RESPONSE_JSON,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertGet()
                assertPathEqual("/event/conversations")
            }
        )

        val api: NomadDeviceSyncApi = NomadDeviceSyncApiV0(networkClient, nomadServiceUrl = "https://nomad.example.com")
        val response = api.getConversationMetadata()

        assertTrue(response.isSuccessful())
        assertEquals(1, response.value.conversations.size)
        assertEquals(1707235100L, response.value.conversations.first().metadata.lastRead)
        assertEquals(1707235200L, response.value.conversations.first().metadata.lastModified)
    }

    @Test
    fun givenConversationMetadataWithoutLastRead_whenGettingConversationMetadata_thenResponseShouldAllowNullLastRead() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            responseBody = CONVERSATION_METADATA_RESPONSE_WITH_NULL_LAST_READ_JSON,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertGet()
                assertPathEqual("/event/conversations")
            }
        )

        val api: NomadDeviceSyncApi = NomadDeviceSyncApiV0(networkClient, nomadServiceUrl = "https://nomad.example.com")
        val response = api.getConversationMetadata()

        assertTrue(response.isSuccessful())
        assertEquals(1, response.value.conversations.size)
        assertEquals(null, response.value.conversations.first().metadata.lastRead)
        assertEquals(1707235200L, response.value.conversations.first().metadata.lastModified)
    }

    @Test
    fun givenCustomNomadServiceUrl_whenGettingAllMessages_thenNomadBaseUrlShouldOverrideDefaultBackendUrl() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            responseBody = ALL_MESSAGES_RESPONSE_JSON,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertGet()
                assertEquals("nomad.example.com", url.host)
                assertEquals(URLProtocol.HTTPS, url.protocol)
                assertPathEqual("/service/event/messages")
            }
        )

        val api: NomadDeviceSyncApi = NomadDeviceSyncApiV0(
            networkClient,
            nomadServiceUrl = "https://nomad.example.com/service"
        )
        val response = api.getAllMessages()

        assertTrue(response.isSuccessful())
    }

    @Test
    fun givenCustomNomadServiceUrl_whenSyncingAllMessages_thenNomadBaseUrlShouldOverrideDefaultBackendUrl() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            responseBody = ALL_MESSAGES_RESPONSE_JSON,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertGet()
                assertEquals("nomad.example.com", url.host)
                assertEquals(URLProtocol.HTTPS, url.protocol)
                assertPathEqual("/service/event/messages/sync")
                assertQueryParameter("limit", "100")
            }
        )

        val api: NomadDeviceSyncApi = NomadDeviceSyncApiV0(
            networkClient,
            nomadServiceUrl = "https://nomad.example.com/service"
        )
        val response = api.syncAllMessages()

        assertTrue(response.isSuccessful())
    }

    @Test
    fun givenNomadServiceUrlWithBasePath_whenRestoringBatch_thenBasePathShouldBePreserved() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            responseBody = BATCH_RESTORE_RESPONSE_JSON,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertGet()
                assertEquals("nomad.example.com", url.host)
                assertEquals(URLProtocol.HTTPS, url.protocol)
                assertPathEqual("/service/event/messages/batch/restore")
                assertQueryParameter("conversation_ids", "conv123")
                assertQueryParameter("limit", "10")
                assertQueryParameter("before_timestamp", "1710936000")
                assertQueryParameter("next_cursor", "123")
            }
        )

        val api: NomadDeviceSyncApi = NomadDeviceSyncApiV0(
            networkClient,
            nomadServiceUrl = "https://nomad.example.com/service"
        )
        val response = api.restoreMessagesBatch(
            NomadBatchRestoreRequest(
                conversationIds = listOf("conv123"),
                limit = 10,
                beforeTimestamp = 1_710_936_000,
                nextCursor = 123
            )
        )

        assertTrue(response.isSuccessful())
    }

    @Test
    fun givenNomadServiceUrlWithBasePath_whenPostingEvents_thenBasePathShouldBePreserved() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            responseBody = "",
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertPost()
                assertJson()
                assertEquals("nomad.example.com", url.host)
                assertEquals(URLProtocol.HTTPS, url.protocol)
                assertPathEqual("/service/event/messages")
            }
        )

        val api: NomadDeviceSyncApi = NomadDeviceSyncApiV0(
            networkClient,
            nomadServiceUrl = "https://nomad.example.com/service"
        )
        val response = api.postMessageEvents(REQUEST)

        assertTrue(response.isSuccessful())
    }

    @Test
    fun givenNomadServiceUrlWithBasePath_whenGettingConversationMetadata_thenBasePathShouldBePreserved() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            responseBody = CONVERSATION_METADATA_RESPONSE_JSON,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertGet()
                assertEquals("nomad.example.com", url.host)
                assertEquals(URLProtocol.HTTPS, url.protocol)
                assertPathEqual("/service/event/conversations")
            }
        )

        val api: NomadDeviceSyncApi = NomadDeviceSyncApiV0(
            networkClient,
            nomadServiceUrl = "https://nomad.example.com/service"
        )
        val response = api.getConversationMetadata()

        assertTrue(response.isSuccessful())
    }

    @Test
    fun givenNomadServiceUrlWithBasePath_whenUploadingCryptoState_thenBasePathShouldBePreserved() = runTest {
        val cryptoStateBytes = byteArrayOf(1, 2, 3, 4)
        val networkClient = mockAuthenticatedNetworkClient(
            responseBody = "",
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertPost()
                assertEquals("nomad.example.com", url.host)
                assertEquals(URLProtocol.HTTPS, url.protocol)
                assertPathEqual("/service/event/crypto/state")
                assertQueryParameter("device_id", "clientId")
            }
        )

        val api: NomadDeviceSyncApi = NomadDeviceSyncApiV0(
            networkClient,
            nomadServiceUrl = "https://nomad.example.com/service"
        )
        val response = api.uploadCryptoState(
            clientId = "clientId",
            backupSource = { Buffer().write(cryptoStateBytes) },
            backupSize = cryptoStateBytes.size.toLong()
        )

        assertTrue(response.isSuccessful())
    }

    @Test
    fun givenNomadServiceUrlWithDeepBasePath_whenGettingAllMessages_thenFullBasePathShouldBePreserved() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            responseBody = ALL_MESSAGES_RESPONSE_JSON,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertGet()
                assertEquals("nomad.example.com", url.host)
                assertEquals(URLProtocol.HTTPS, url.protocol)
                assertPathEqual("/api/v1/event/messages")
            }
        )

        val api: NomadDeviceSyncApi = NomadDeviceSyncApiV0(
            networkClient,
            nomadServiceUrl = "https://nomad.example.com/api/v1"
        )
        val response = api.getAllMessages()

        assertTrue(response.isSuccessful())
    }

    @Test
    fun givenNomadServiceUrlWithDeepBasePath_whenSyncingAllMessages_thenFullBasePathShouldBePreserved() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            responseBody = ALL_MESSAGES_RESPONSE_JSON,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertGet()
                assertEquals("nomad.example.com", url.host)
                assertEquals(URLProtocol.HTTPS, url.protocol)
                assertPathEqual("/api/v1/event/messages/sync")
                assertQueryParameter("limit", "100")
            }
        )

        val api: NomadDeviceSyncApi = NomadDeviceSyncApiV0(
            networkClient,
            nomadServiceUrl = "https://nomad.example.com/api/v1"
        )
        val response = api.syncAllMessages()

        assertTrue(response.isSuccessful())
    }

    @Test
    fun givenEmptyConversationMetadataEvent_whenConstructingMessageEvent_thenItShouldThrow() {
        val exception = assertFailsWith<IllegalArgumentException> {
            NomadMessageEvent.ConversationMetadataEvent(conversationMetadata = emptyList())
        }

        assertFalse(exception.message.isNullOrBlank())
    }

    @Test
    fun givenCryptoState_whenUploading_thenRequestShouldMatchContract() = runTest {
        val cryptoStateBytes = byteArrayOf(1, 2, 3, 4)
        val networkClient = mockAuthenticatedNetworkClient(
            responseBody = "",
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertPost()
                assertContentType(
                    ContentType.MultiPart.FormData.withParameter("boundary", "frontier")
                )
                assertPathEqual("/event/crypto/state")
                assertQueryParameter("device_id", "clientId")
            }
        )

        val api: NomadDeviceSyncApi = NomadDeviceSyncApiV0(networkClient, nomadServiceUrl = "https://nomad.example.com")
        val response = api.uploadCryptoState(
            clientId = "clientId",
            backupSource = { Buffer().write(cryptoStateBytes) },
            backupSize = cryptoStateBytes.size.toLong()
        )

        assertTrue(response.isSuccessful())
    }

    @Test
    fun givenMissingNomadServiceUrl_whenPostingEvents_thenItShouldShortCircuitWithoutNetworkCall() = runTest {
        assertShortCircuitedWithoutNetworkCall(expectedApiName = "postMessageEvents") { api ->
            api.postMessageEvents(REQUEST)
        }
    }

    @Test
    fun givenMissingNomadServiceUrl_whenGettingAllMessages_thenItShouldShortCircuitWithoutNetworkCall() = runTest {
        assertShortCircuitedWithoutNetworkCall(expectedApiName = "getAllMessages") { api ->
            api.getAllMessages()
        }
    }

    @Test
    fun givenMissingNomadServiceUrl_whenSyncingAllMessages_thenItShouldShortCircuitWithoutNetworkCall() = runTest {
        assertShortCircuitedWithoutNetworkCall(expectedApiName = "syncAllMessages") { api ->
            api.syncAllMessages()
        }
    }

    @Test
    fun givenMissingNomadServiceUrl_whenRestoringBatch_thenItShouldShortCircuitWithoutNetworkCall() = runTest {
        assertShortCircuitedWithoutNetworkCall(expectedApiName = "restoreMessagesBatch") { api ->
            api.restoreMessagesBatch(
                NomadBatchRestoreRequest(
                    conversationIds = listOf("conv123"),
                    limit = 10,
                    beforeTimestamp = 1_710_936_000,
                    nextCursor = 0
                )
            )
        }
    }

    @Test
    fun givenMissingNomadServiceUrl_whenGettingConversationMetadata_thenItShouldShortCircuitWithoutNetworkCall() = runTest {
        assertShortCircuitedWithoutNetworkCall(expectedApiName = "getConversationMetadata") { api ->
            api.getConversationMetadata()
        }
    }

    @Test
    fun givenNomadServiceUrlWithBasePath_whenSyncingAllMessages_thenBasePathShouldBePreserved() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            responseBody = ALL_MESSAGES_RESPONSE_JSON,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertGet()
                assertEquals("nomad.example.com", url.host)
                assertEquals(URLProtocol.HTTPS, url.protocol)
                assertPathEqual("/service/event/messages/sync")
                assertQueryParameter("limit", "150")
            }
        )

        val api: NomadDeviceSyncApi = NomadDeviceSyncApiV0(
            networkClient,
            nomadServiceUrl = "https://nomad.example.com/service"
        )
        val response = api.syncAllMessages(limit = 150)

        assertTrue(response.isSuccessful())
    }

    @Test
    fun givenMissingNomadServiceUrl_whenUploadingCryptoState_thenItShouldShortCircuitWithoutNetworkCall() = runTest {
        assertShortCircuitedWithoutNetworkCall(expectedApiName = "uploadCryptoState") { api ->
            api.uploadCryptoState(
                clientId = "clientId",
                backupSource = { Buffer().write(byteArrayOf(1, 2, 3, 4)) },
                backupSize = 4
            )
        }
    }

    @Test
    fun givenMissingNomadServiceUrl_whenDownloadingCryptoState_thenItShouldShortCircuitWithoutNetworkCall() = runTest {
        assertShortCircuitedWithoutNetworkCall(expectedApiName = "downloadCryptoState") { api ->
            api.downloadCryptoState(Buffer())
        }
    }

    @Test
    fun givenSuccessfulResponse_whenDownloadingCryptoState_thenRequestIsGetToCorrectPathAndDataIsWrittenToSink() = runTest {
        val responseContent = "crypto state binary content"
        val networkClient = mockAuthenticatedNetworkClient(
            responseBody = responseContent,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertGet()
                assertPathEqual("/event/crypto/state")
            }
        )

        val sink = Buffer()
        val api: NomadDeviceSyncApi = NomadDeviceSyncApiV0(networkClient, nomadServiceUrl = "https://nomad.example.com")
        val response = api.downloadCryptoState(sink)

        assertTrue(response.isSuccessful())
        assertEquals(responseContent, sink.readUtf8())
    }

    @Test
    fun givenUnauthorizedWithUserNotFoundLabel_whenDownloadingCryptoState_thenReturnUserNotFoundError() = runTest {
        val networkClient = mockNetworkClientWithTokenRefresh(
            responseBody = """{"code":401,"label":"user_not_found","message":"user not found"}""",
            statusCode = HttpStatusCode.Unauthorized
        )

        val api: NomadDeviceSyncApi = NomadDeviceSyncApiV0(networkClient, nomadServiceUrl = "https://nomad.example.com")
        val response = api.downloadCryptoState(Buffer())

        val error = assertIs<NetworkResponse.Error>(response)
        val exception = assertIs<KaliumException.InvalidRequestError>(error.kException)
        assertTrue(exception.isUserNotFound())
    }

    @Test
    fun givenUnauthorizedWithoutMatchingLabel_whenDownloadingCryptoState_thenReturnGenericUnauthorizedError() = runTest {
        val networkClient = mockNetworkClientWithTokenRefresh(
            responseBody = """{"code":401,"label":"some-other-label","message":"unauthorized"}""",
            statusCode = HttpStatusCode.Unauthorized
        )

        val api: NomadDeviceSyncApi = NomadDeviceSyncApiV0(networkClient, nomadServiceUrl = "https://nomad.example.com")
        val response = api.downloadCryptoState(Buffer())

        val error = assertIs<NetworkResponse.Error>(response)
        assertIs<KaliumException.Unauthorized>(error.kException)
    }

    @Test
    fun givenForbiddenWithNoCryptoStateLabel_whenDownloadingCryptoState_thenReturnNoCryptoStateError() = runTest {
        val networkClient = mockNetworkClientWithTokenRefresh(
            responseBody = """{"code":403,"label":"no_crypto_state","message":"no crypto state"}""",
            statusCode = HttpStatusCode.Forbidden
        )

        val api: NomadDeviceSyncApi = NomadDeviceSyncApiV0(networkClient, nomadServiceUrl = "https://nomad.example.com")
        val response = api.downloadCryptoState(Buffer())

        val error = assertIs<NetworkResponse.Error>(response)
        val exception = assertIs<KaliumException.InvalidRequestError>(error.kException)
        assertTrue(exception.isNoCryptoState())
    }

    @Test
    fun givenForbiddenWithoutMatchingLabel_whenDownloadingCryptoState_thenReturnGenericForbiddenError() = runTest {
        val networkClient = mockNetworkClientWithTokenRefresh(
            responseBody = """{"code":403,"label":"some-other-label","message":"forbidden"}""",
            statusCode = HttpStatusCode.Forbidden
        )

        val api: NomadDeviceSyncApi = NomadDeviceSyncApiV0(networkClient, nomadServiceUrl = "https://nomad.example.com")
        val response = api.downloadCryptoState(Buffer())

        val error = assertIs<NetworkResponse.Error>(response)
        val exception = assertIs<KaliumException.InvalidRequestError>(error.kException)
        assertFalse(exception.isNoCryptoState())
    }

    @Test
    fun givenServerError_whenDownloadingCryptoState_thenReturnError() = runTest {
        val networkClient = mockNetworkClientWithTokenRefresh(
            responseBody = """{"code":500,"label":"internal-server-error","message":"server error"}""",
            statusCode = HttpStatusCode.InternalServerError
        )

        val api: NomadDeviceSyncApi = NomadDeviceSyncApiV0(networkClient, nomadServiceUrl = "https://nomad.example.com")
        val response = api.downloadCryptoState(Buffer())

        assertIs<NetworkResponse.Error>(response)
    }

    private suspend fun assertShortCircuitedWithoutNetworkCall(
        expectedApiName: String,
        call: suspend (NomadDeviceSyncApi) -> NetworkResponse<*>
    ) {
        var networkCalled = false
        val networkClient = mockAuthenticatedNetworkClient(
            responseBody = "",
            statusCode = HttpStatusCode.OK,
            assertion = {
                networkCalled = true
            }
        )

        val response = call(NomadDeviceSyncApiV0(networkClient, nomadServiceUrl = null))

        assertFalse(networkCalled)
        val error = assertIs<NetworkResponse.Error>(response)
        val exception = assertIs<APINotSupported>(error.kException)
        assertEquals(
            "NomadDeviceSyncApiV0.$expectedApiName requires a configured Nomad service URL. " +
                    "Request was short-circuited and no API call was made.",
            exception.errorBody
        )
    }

    /**
     * Creates a mock client where [TestSessionManagerV0.updateToken] returns a dummy session
     * instead of throwing. This allows Ktor's bearer auth plugin to complete its token-refresh
     * cycle when it intercepts a 4xx response, so the retry reaches our response-handling logic.
     */
    private fun mockNetworkClientWithTokenRefresh(
        responseBody: String,
        statusCode: HttpStatusCode,
    ): AuthenticatedNetworkClient {
        val mockEngine = MockEngine { _ ->
            respond(
                content = ByteReadChannel(responseBody),
                status = statusCode,
                headers = io.ktor.http.HeadersImpl(
                    mapOf(io.ktor.http.HttpHeaders.ContentType to listOf("application/json"))
                )
            )
        }
        val sessionManager = object : SessionManager {
            override suspend fun session(): SessionDTO = testCredentials
            override fun serverConfig(): ServerConfigDTO = TEST_BACKEND_CONFIG
            override fun nomadServiceUrl(): String? = null
            override suspend fun updateToken(
                accessTokenApi: AccessTokenApi,
                oldRefreshToken: String?
            ): SessionDTO = testCredentials

            override fun proxyCredentials(): ProxyCredentialsDTO? = null
        }
        return AuthenticatedNetworkContainerV0(
            engine = mockEngine,
            sessionManager = sessionManager,
            certificatePinning = emptyMap(),
            mockEngine = null,
            kaliumLogger = kaliumLogger,
            mockWebSocketSession = null
        ).networkClient
    }

    private companion object {
        val REQUEST = NomadMessageEventsRequest(
            events = listOf(
                NomadMessageEvent.ConversationMetadataEvent(
                    conversationMetadata = listOf(
                        ConversationMetadataEntry(
                            conversation = Conversation(id = "conv_1", domain = "example.com"),
                            lastReadTimestamp = 1772014500000,
                            lastModifiedTimestamp = 1772014600000
                        ),
                        ConversationMetadataEntry(
                            conversation = Conversation(id = "conv_2", domain = "example.com"),
                            lastReadTimestamp = 1772014800000,
                            lastModifiedTimestamp = 1772014900000
                        )
                    )
                )
            )
        )

        val EXPECTED_REQUEST_JSON =
            """
            {
              "events": [
                {
                  "type": "CONVERSATION_METADATA",
                  "conversation_metadata": [
                    {
                      "conversation": {
                        "id": "conv_1",
                        "domain": "example.com"
                      },
                      "last_read": 1772014500000,
                      "last_modified": 1772014600000
                    },
                    {
                      "conversation": {
                        "id": "conv_2",
                        "domain": "example.com"
                      },
                      "last_read": 1772014800000,
                      "last_modified": 1772014900000
                    }
                  ]
                }
              ]
            }
            """.trimIndent()

        const val REACTION_RAW =
            "{\"reactions_by_user\":[{\"user_id\":{\"id\":\"user-1\",\"domain\":\"example.com\"},\"emojis\":[\"👍\"]}]}"

        const val READ_RECEIPT_RAW =
            "{\"read_receipts\":[{\"user_id\":{\"id\":\"user-1\",\"domain\":\"example.com\"},\"date\":\"2026-02-25T10:15:00Z\"}]}"

        val REACTION_RAW_JSON = REACTION_RAW.replace("\"", "\\\"")
        val READ_RECEIPT_RAW_JSON = READ_RECEIPT_RAW.replace("\"", "\\\"")

        val ALL_MESSAGES_RESPONSE_JSON =
            """
            {
              "conversations": [
                {
                  "conversation": {
                    "id": "conv-123",
                    "domain": "example.com"
                  },
                  "messages": [
                    {
                      "message_id": "msg-001234",
                      "timestamp": 1707235200,
                      "payload": "SGVsbG8gV29ybGQ=",
                      "reaction": "$REACTION_RAW_JSON",
                      "read_receipt": "$READ_RECEIPT_RAW_JSON"
                    },
                    {
                      "message_id": "msg-00123",
                      "timestamp": 1707235200,
                      "payload": "SGVsbG8gV29ybGQ=",
                      "reaction": "$REACTION_RAW_JSON",
                      "read_receipt": "$READ_RECEIPT_RAW_JSON"
                    }
                  ]
                },
                {
                  "conversation": {
                    "id": "conv-12345",
                    "domain": "example.com"
                  },
                  "messages": [
                    {
                      "message_id": "msg-00123",
                      "timestamp": 1707235200,
                      "payload": "SGVsbG8gV29ybGQ=",
                      "reaction": "$REACTION_RAW_JSON",
                      "read_receipt": "$READ_RECEIPT_RAW_JSON"
                    },
                    {
                      "message_id": "msg-001234",
                      "timestamp": 1707235200,
                      "payload": "SGVsbG8gV29ybGQ=",
                      "reaction": "$REACTION_RAW_JSON",
                      "read_receipt": "$READ_RECEIPT_RAW_JSON"
                    }
                  ]
                }
              ]
            }
            """.trimIndent()

        val CONVERSATION_METADATA_RESPONSE_JSON =
            """
            {
              "conversations": [
                {
                  "conversation": {
                    "id": "conv-12345",
                    "domain": "example.com"
                  },
                  "metadata": {
                    "last_read": 1707235100,
                    "last_modified": 1707235200
                  }
                }
              ]
            }
            """.trimIndent()

        val CONVERSATION_METADATA_RESPONSE_WITH_NULL_LAST_READ_JSON =
            """
            {
              "conversations": [
                {
                  "conversation": {
                    "id": "conv-12345",
                    "domain": "example.com"
                  },
                  "metadata": {
                    "last_read": null,
                    "last_modified": 1707235200
                  }
                }
              ]
            }
            """.trimIndent()

        val BATCH_RESTORE_RESPONSE_JSON =
            """
            {
              "conversations": [
                {
                  "conversation": {
                    "id": "conv123",
                    "domain": "example.com"
                  },
                  "messages": [
                    {
                      "message_id": "msg099",
                      "timestamp": 1710935900,
                      "payload": "T2xkZXIgbWVzc2FnZQ==",
                      "reaction": "",
                      "read_receipt": ""
                    }
                  ],
                  "has_more": true,
                  "next_cursor": 12345,
                  "next_timestamp": 1710935700
                },
                {
                  "conversation": {
                    "id": "conv456",
                    "domain": "example.com"
                  },
                  "messages": [],
                  "has_more": false,
                  "next_cursor": 0,
                  "next_timestamp": 0
                }
              ]
            }
            """.trimIndent()
    }
}
