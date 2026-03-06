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
import com.wire.kalium.network.api.authenticated.nomaddevice.LastRead
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadMessageEvent
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadMessageEventsRequest
import com.wire.kalium.network.api.base.authenticated.nomaddevice.NomadDeviceSyncApi
import com.wire.kalium.network.api.v0.authenticated.NomadDeviceSyncApiV0
import com.wire.kalium.network.exceptions.APINotSupported
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
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
                assertPathEqual("/event/all-messages")
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
    fun givenConversationMetadata_whenGettingConversationMetadata_thenRequestAndResponseShouldMatchContract() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            responseBody = CONVERSATION_METADATA_RESPONSE_JSON,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertGet()
                assertPathEqual("/event/conversation/metadata")
            }
        )

        val api: NomadDeviceSyncApi = NomadDeviceSyncApiV0(networkClient, nomadServiceUrl = "https://nomad.example.com")
        val response = api.getConversationMetadata()

        assertTrue(response.isSuccessful())
        assertEquals(1, response.value.conversations.size)
        assertEquals(1707235100L, response.value.conversations.first().metadata.lastRead)
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
                assertPathEqual("/service/event/all-messages")
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
    fun givenEmptyLastReadEvent_whenConstructingMessageEvent_thenItShouldThrow() {
        val exception = assertFailsWith<IllegalArgumentException> {
            NomadMessageEvent.LastReadEvent(lastRead = emptyList())
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
    fun givenMissingNomadServiceUrl_whenGettingConversationMetadata_thenItShouldShortCircuitWithoutNetworkCall() = runTest {
        assertShortCircuitedWithoutNetworkCall(expectedApiName = "getConversationMetadata") { api ->
            api.getConversationMetadata()
        }
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

    private companion object {
        val REQUEST = NomadMessageEventsRequest(
            events = listOf(
                NomadMessageEvent.LastReadEvent(
                    lastRead = listOf(
                        LastRead(conversationId = "conv_1", lastReadTimestamp = 1772014500000),
                        LastRead(conversationId = "conv_2", lastReadTimestamp = 1772014800000)
                    )
                )
            )
        )

        val EXPECTED_REQUEST_JSON =
            """
            {
              "events": [
                {
                  "type": "last_read",
                  "last_read": [
                    {
                      "conversation_id": "conv_1",
                      "last_read": 1772014500000
                    },
                    {
                      "conversation_id": "conv_2",
                      "last_read": 1772014800000
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
                    "last_read": 1707235100
                  }
                }
              ]
            }
            """.trimIndent()
    }
}
