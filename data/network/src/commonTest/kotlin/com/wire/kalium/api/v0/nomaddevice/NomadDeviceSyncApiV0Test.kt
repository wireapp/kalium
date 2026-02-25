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
import com.wire.kalium.api.tools.IgnoreIOS
import com.wire.kalium.network.api.authenticated.nomaddevice.LastRead
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadMessageEvent
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadMessageEventsRequest
import com.wire.kalium.network.api.base.authenticated.nomaddevice.NomadDeviceSyncApi
import com.wire.kalium.network.api.v0.authenticated.NomadDeviceSyncApiV0
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@IgnoreIOS
internal class NomadDeviceSyncApiV0Test : ApiTest() {

    @Test
    fun givenNomadEvents_whenPosting_thenRequestShouldMatchContract() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            responseBody = "",
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertPost()
                assertJson()
                assertPathEqual("/message/events")
                assertJsonBodyContent(EXPECTED_REQUEST_JSON)
            }
        )

        val api: NomadDeviceSyncApi = NomadDeviceSyncApiV0(networkClient)
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
                assertPathEqual("/all-messages")
            }
        )

        val api: NomadDeviceSyncApi = NomadDeviceSyncApiV0(networkClient)
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
                assertPathEqual("/conversation/metadata")
            }
        )

        val api: NomadDeviceSyncApi = NomadDeviceSyncApiV0(networkClient)
        val response = api.getConversationMetadata()

        assertTrue(response.isSuccessful())
        assertEquals(1, response.value.conversations.size)
        assertEquals(1707235100L, response.value.conversations.first().metadata.lastRead)
    }

    @Test
    fun givenEmptyLastReadEvent_whenConstructingMessageEvent_thenItShouldThrow() {
        val exception = assertFailsWith<IllegalArgumentException> {
            NomadMessageEvent.LastReadEvent(lastRead = emptyList())
        }

        assertFalse(exception.message.isNullOrBlank())
    }

    private companion object {
        val REQUEST = NomadMessageEventsRequest(
            events = listOf(
                NomadMessageEvent.LastReadEvent(
                    lastRead = listOf(
                        LastRead(conversationId = "conv_1", lastRead = "2026-02-25T10:15:00Z"),
                        LastRead(conversationId = "conv_2", lastRead = "2026-02-25T10:20:00Z")
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
                  "lastRead": [
                    {
                      "conversation_id": "conv_1",
                      "last_read": "2026-02-25T10:15:00Z"
                    },
                    {
                      "conversation_id": "conv_2",
                      "last_read": "2026-02-25T10:20:00Z"
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
