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
package com.wire.kalium.api.base.authenticated.notification

import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.authenticated.notification.conversation.MLSMessagePayloadEventData
import com.wire.kalium.network.api.model.ConversationId
import com.wire.kalium.network.api.model.UserId
import com.wire.kalium.network.tools.KtxSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class MLSPayloadMessageEventTest {

    @Test
    fun givenTargetedMLSMessageEvent_whenDecoding_thenReturnsTargetedMessageDTO() {
        val result = KtxSerializer.json.decodeFromString(EventContentDTO.serializer(), eventJson("conversation.mls-message-targeted"))

        assertEquals(
            EventContentDTO.Conversation.NewMLSTargetedMessageDTO(CONVERSATION_ID, SENDER_ID, TIME, MLSMessagePayloadEventData(PAYLOAD)),
            result
        )
    }

    @Test
    fun givenTransientMLSMessageEvent_whenDecoding_thenReturnsTransientMessageDTO() {
        val result = KtxSerializer.json.decodeFromString(EventContentDTO.serializer(), eventJson("conversation.mls-message-transient"))

        assertEquals(
            EventContentDTO.Conversation.NewMLSTransientMessageDTO(CONVERSATION_ID, SENDER_ID, TIME, MLSMessagePayloadEventData(PAYLOAD)),
            result
        )
    }

    @Test
    fun givenTransientTargetedMLSMessageEvent_whenDecoding_thenReturnsTransientTargetedMessageDTO() {
        val result = KtxSerializer.json.decodeFromString(
            EventContentDTO.serializer(),
            TRANSIENT_MESSAGE_EVENT_JSON.replace("conversation.mls-message-transient", "conversation.mls-message-transient-targeted")
        )

        assertEquals(
            EventContentDTO.Conversation.NewMLSTransientTargetedMessageDTO(
                qualifiedConversation = ConversationId("454710e5-5c6a-4ff0-8548-0fa7ba3787dc", "anta.wire.link"),
                qualifiedFrom = UserId("5efa468a-5380-4058-8e6b-fd20c70c3c68", "anta.wire.link"),
                time = Instant.parse("2026-10-20T13:39:32.138Z"),
                message = TRANSIENT_MESSAGE
            ),
            result
        )
    }

    private companion object {
        const val PAYLOAD = "AAEAASMAAgAAGNueV/bHQK+HYzRSu9ayBwAAAAAyAAh3aXJlLmNvbQAAAAAAAAAGBAADQE0BAAZAQQRB0SpcGDk"
        val CONVERSATION_ID = ConversationId("18db9e57-f6c7-40af-8763-3452bbd6b207", "wire.com")
        val SENDER_ID = UserId("fb051b02-f758-48a0-bec2-0e5f9a5b7472", "wire.com")
        val TIME = Instant.parse("2026-03-31T13:05:08.280Z")

        fun eventJson(type: String) = """
            {
                "data": {
                    "payload": "$PAYLOAD"
                },
                "qualified_conversation": {
                    "domain": "wire.com",
                    "id": "18db9e57-f6c7-40af-8763-3452bbd6b207"
                },
                "qualified_from": {
                    "domain": "wire.com",
                    "id": "fb051b02-f758-48a0-bec2-0e5f9a5b7472"
                },
                "time": "2026-03-31T13:05:08.280Z",
                "type": "$type",
                "via": "user"
            }
        """.trimIndent()
    }
}
