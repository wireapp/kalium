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

import com.wire.kalium.network.tools.KtxSerializer
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

class AdminlessDeleteReminderEventSerializationTest {

    @Test
    fun givenAdminlessReminderEnvelope_whenDeserializing_thenAllFieldsArePreserved() {
        val result = KtxSerializer.json.decodeFromString<EventResponse>(
            eventEnvelopeJson(
                type = "conversation.adminless-reminder",
                qualifiedFromJson = QUALIFIED_FROM_JSON,
            )
        )

        assertEquals(EVENT_ID, result.id)
        assertFalse(result.transient)
        val event = assertIs<EventContentDTO.Conversation.AdminlessDeleteReminderDTO>(result.payload?.single())
        assertEquals(CONVERSATION_ID, event.conversation)
        assertEquals(CONVERSATION_ID, event.qualifiedConversation.value)
        assertEquals(DOMAIN, event.qualifiedConversation.domain)
        assertEquals(USER_ID, event.from)
        assertEquals(USER_ID, event.qualifiedFrom.value)
        assertEquals(DOMAIN, event.qualifiedFrom.domain)
        assertEquals(TEAM_ID, event.teamId)
        assertEquals("user", event.via)
        assertEquals(EVENT_TIME, event.time)
        assertEquals(DELETION_TIME, event.data.deletionScheduledFor)
    }

    @Test
    fun givenSystemAdminlessReminderWithQualifiedFrom_whenDeserializing_thenSenderIsPreserved() {
        val result = deserializeSystemEvent(QUALIFIED_FROM_JSON)

        assertEquals(USER_ID, result.qualifiedFrom?.value)
    }

    @Test
    fun givenSystemAdminlessReminderWithoutQualifiedFrom_whenDeserializing_thenSenderIsNull() {
        val result = deserializeSystemEvent()

        assertNull(result.qualifiedFrom)
    }

    @Test
    fun givenSystemAdminlessReminderWithNullQualifiedFrom_whenDeserializing_thenSenderIsNull() {
        val result = deserializeSystemEvent(""""qualified_from": null,""")

        assertNull(result.qualifiedFrom)
    }

    private fun deserializeSystemEvent(
        qualifiedFromJson: String = ""
    ): EventContentDTO.Conversation.SystemAdminlessDeleteReminderDTO {
        val result = KtxSerializer.json.decodeFromString<EventResponse>(
            eventEnvelopeJson(
                type = "conversation.system.adminless-reminder",
                qualifiedFromJson = qualifiedFromJson,
            )
        )
        return assertIs(result.payload?.single())
    }

    private fun eventEnvelopeJson(type: String, qualifiedFromJson: String) = """
        {
          "id": "$EVENT_ID",
          "payload": [
            {
              "conversation": "$CONVERSATION_ID",
              "data": {
                "deletion_scheduled_for": "$DELETION_TIME"
              },
              "from": "$USER_ID",
              "qualified_conversation": {
                "domain": "$DOMAIN",
                "id": "$CONVERSATION_ID"
              },
              $qualifiedFromJson
              "team": "$TEAM_ID",
              "time": "$EVENT_TIME",
              "type": "$type",
              "via": "user"
            }
          ],
          "transient": false
        }
    """.trimIndent()

    private companion object {
        const val EVENT_ID = "00000000-0000-0000-0000-000000000010"
        const val CONVERSATION_ID = "00000000-0000-0000-0000-000000000002"
        const val USER_ID = "00000000-0000-0000-0000-000000000003"
        const val TEAM_ID = "00000000-0000-0000-0000-000000000001"
        const val DOMAIN = "example.com"
        val EVENT_TIME = Instant.parse("2026-07-16T12:00:00.000Z")
        val DELETION_TIME = Instant.parse("2026-07-20T12:00:00.000Z")
        const val QUALIFIED_FROM_JSON = """
            "qualified_from": {
              "domain": "$DOMAIN",
              "id": "$USER_ID"
            },
        """
    }
}
