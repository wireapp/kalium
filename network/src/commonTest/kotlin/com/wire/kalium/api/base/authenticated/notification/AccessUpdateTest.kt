/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import com.wire.kalium.model.EventContentDTOJson
import com.wire.kalium.network.api.base.authenticated.conversation.model.JsonCorrectingSerializer
import com.wire.kalium.network.api.base.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.base.model.ConversationAccessRoleDTO
import com.wire.kalium.network.tools.KtxSerializer
import kotlin.test.Test
import kotlin.test.assertEquals

class AccessUpdateTest {

    private val json get() = KtxSerializer.json

    @Test
    fun givenPayload_whenDecoding_thenSuccess() {
        val result = json.decodeFromString(
            EventContentDTO.Conversation.AccessUpdate.serializer(),
            EventContentDTOJson.validAccessUpdate.rawJson
        )

        assertEquals(result, EventContentDTOJson.validAccessUpdate.serializableData)
    }

    @Test
    fun givenPayloadWithDeprecatedAccessRoleField_whenDecoding_thenSuccess() {
        val result = json.decodeFromString(
            EventContentDTO.Conversation.AccessUpdate.serializer(),
            EventContentDTOJson.validAccessUpdateWithDeprecatedAccessRoleField.rawJson
        )

        assertEquals(result, EventContentDTOJson.validAccessUpdate.serializableData)
    }

    @Test
    fun givenPayloadWithAccessRoleAndDeprecatedAccessRoleField_whenDecoding_thenDeprecatedFieldIsPreferred() {
        val result = json.decodeFromString(
            JsonCorrectingSerializer,
            """
                {
                    "access": ["invite"],
                    "access_role": "team_member",
                    "access_role_v2": ["guest"]
                }
            """.trimIndent()
        )

        assertEquals(result.accessRole, setOf(ConversationAccessRoleDTO.GUEST))
    }

}
