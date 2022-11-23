package com.wire.kalium.api.base.authenticated.notification

import com.wire.kalium.model.EventContentDTOJson
import com.wire.kalium.network.api.base.authenticated.conversation.model.ConversationAccessInfoDTO
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
            ConversationAccessInfoDTO.serializer(),
            """
                {
                    "access": ["invite"]
                    "access_role": ["team_member"]
                    "access_role_v2": ["guest"]
                }
            """.trimIndent()
        )

        assertEquals(result.accessRole, setOf(ConversationAccessRoleDTO.GUEST))
    }

}
