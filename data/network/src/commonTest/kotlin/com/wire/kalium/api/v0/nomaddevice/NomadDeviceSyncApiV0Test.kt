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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

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
                  "lastRead": [
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
    }
}
