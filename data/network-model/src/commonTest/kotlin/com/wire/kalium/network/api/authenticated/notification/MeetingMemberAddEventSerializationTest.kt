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

package com.wire.kalium.network.api.authenticated.notification

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class MeetingMemberAddEventSerializationTest {
    @Test
    fun givenOptionalSender_whenDecoding_thenAcceptPresentMissingAndNull() {
        listOf(
            "" to null,
            """"qualified_from":null,""" to null,
            """"qualified_from":{"id":"inviter","domain":"example.com"},""" to "inviter"
        ).forEach { (sender, expected) ->
            val event = Json.decodeFromString<EventContentDTO.Meeting.MeetingMemberAddDTO>(
                """
                   {
                        $sender
                        "qualified_id":{"id":"meeting","domain":"example.com"},
                        "time":"2026-09-23T12:00:00Z"
                   }
                """.trimIndent()
            )
            assertEquals(expected, event.qualifiedFrom?.value)
        }
    }
}
