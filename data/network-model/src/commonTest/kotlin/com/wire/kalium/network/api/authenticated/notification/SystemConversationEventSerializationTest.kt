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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class SystemConversationEventSerializationTest {

    @Test
    fun givenSystemDeleteWithoutQualifiedFrom_whenDeserializing_thenSenderIsNull() {
        val result = KtxSerializer.json.decodeFromString<EventContentDTO>(systemDeleteJson())

        val event = assertIs<EventContentDTO.Conversation.SystemDeletedConversationDTO>(result)
        assertNull(event.qualifiedFrom)
        assertEquals(CONVERSATION_ID, event.qualifiedConversation.value)
    }

    @Test
    fun givenSystemDeleteWithQualifiedFrom_whenDeserializing_thenSenderIsPreserved() {
        val result = KtxSerializer.json.decodeFromString<EventContentDTO>(
            systemDeleteJson(qualifiedFromJson = QUALIFIED_FROM_JSON)
        )

        val event = assertIs<EventContentDTO.Conversation.SystemDeletedConversationDTO>(result)
        assertEquals(USER_ID, event.qualifiedFrom?.value)
    }

    @Test
    fun givenSystemMemberUpdateWithoutQualifiedFrom_whenDeserializing_thenSenderIsNull() {
        val result = KtxSerializer.json.decodeFromString<EventContentDTO>(systemMemberUpdateJson())

        val event = assertIs<EventContentDTO.Conversation.SystemMemberUpdateDTO>(result)
        assertNull(event.qualifiedFrom)
        assertEquals(TARGET_USER_ID, event.roleChange.qualifiedUserId.value)
    }

    @Test
    fun givenSystemMemberUpdateWithQualifiedFrom_whenDeserializing_thenSenderIsPreserved() {
        val result = KtxSerializer.json.decodeFromString<EventContentDTO>(
            systemMemberUpdateJson(qualifiedFromJson = QUALIFIED_FROM_JSON)
        )

        val event = assertIs<EventContentDTO.Conversation.SystemMemberUpdateDTO>(result)
        assertEquals(USER_ID, event.qualifiedFrom?.value)
    }

    private fun systemDeleteJson(qualifiedFromJson: String = "") = """
        {
          "type": "conversation.system.delete",
          "qualified_conversation": {
            "id": "$CONVERSATION_ID",
            "domain": "$DOMAIN"
          },
          $qualifiedFromJson
          "time": "$TIME"
        }
    """.trimIndent()

    private fun systemMemberUpdateJson(qualifiedFromJson: String = "") = """
        {
          "type": "conversation.system.member-update",
          "qualified_conversation": {
            "id": "$CONVERSATION_ID",
            "domain": "$DOMAIN"
          },
          $qualifiedFromJson
          "time": "$TIME",
          "from": "system",
          "data": {
            "target": "$TARGET_USER_ID",
            "qualified_target": {
              "id": "$TARGET_USER_ID",
              "domain": "$DOMAIN"
            },
            "conversation_role": "wire_admin",
            "otr_muted_ref": null,
            "otr_muted_status": null,
            "otr_archived": null,
            "otr_archived_ref": null
          }
        }
    """.trimIndent()

    private companion object {
        const val CONVERSATION_ID = "conversation-id"
        const val USER_ID = "user-id"
        const val TARGET_USER_ID = "target-user-id"
        const val DOMAIN = "example.com"
        const val TIME = "2026-07-30T10:00:00.000Z"
        const val QUALIFIED_FROM_JSON = """
            "qualified_from": {
              "id": "$USER_ID",
              "domain": "$DOMAIN"
            },
        """
    }
}
