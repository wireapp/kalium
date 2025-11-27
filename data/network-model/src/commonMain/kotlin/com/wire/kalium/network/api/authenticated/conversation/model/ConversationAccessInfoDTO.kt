/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

package com.wire.kalium.network.api.authenticated.conversation.model

import com.wire.kalium.network.api.model.ConversationAccessDTO
import com.wire.kalium.network.api.model.ConversationAccessRoleDTO
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.jsonObject

/**
 * Handwritten serializer of the ConversationAccessInfoDTO because we want to extend it with the
 * `JsonCorrectingSerializer`, which is not possible using the plugin generated serializer.
 */
object ConversationAccessInfoDTOSerializer : KSerializer<ConversationAccessInfoDTO> {

    override val descriptor: SerialDescriptor = ConversationAccessInfoDTOSurrogate.serializer().descriptor

    override fun deserialize(decoder: Decoder): ConversationAccessInfoDTO {
        val surrogate = decoder.decodeSerializableValue(ConversationAccessInfoDTOSurrogate.serializer())
        return ConversationAccessInfoDTO(surrogate.access, surrogate.accessRole)
    }

    override fun serialize(encoder: Encoder, value: ConversationAccessInfoDTO) {
        val surrogate = ConversationAccessInfoDTOSurrogate(value.access, value.accessRole)
        encoder.encodeSerializableValue(ConversationAccessInfoDTOSurrogate.serializer(), surrogate)
    }
}

/**
 * Transforms the conversation access info JSON by deleting the `access_role` field if it's a primitive
 * value, because then it's a legacy field which interferes with the @JsonNames alternative name parsing.
 */
object JsonCorrectingSerializer :
    JsonTransformingSerializer<ConversationAccessInfoDTO>(ConversationAccessInfoDTOSerializer) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        return JsonObject(
            element.jsonObject.toMutableMap().apply {
                val accessRole = this["access_role"]
                if (accessRole is JsonPrimitive && accessRole.isString) {
                    this.remove("access_role")
                }
            }
        )
    }
}

/**
 * **Deprecation info**: Since API v3 `access_role_v2` is deprecated and will be replaced by `access_role`, but until all servers have
 * been upgraded to have API v3 has its minimum supported version and all previously stored events have expired we need support both cases.
 *
 * Further info: https://wearezeta.atlassian.net/wiki/spaces/ENGINEERIN/pages/672006169/API+changes+v2+v3
 */
@Serializable(with = JsonCorrectingSerializer::class)
@OptIn(ExperimentalSerializationApi::class)
data class ConversationAccessInfoDTO constructor(
    @SerialName("access")
    val access: Set<ConversationAccessDTO>,
    @SerialName("access_role_v2") @JsonNames("access_role")
    val accessRole: Set<ConversationAccessRoleDTO> = ConversationAccessRoleDTO.DEFAULT_VALUE_WHEN_NULL
)

/**
 * Surrogate class of the ConversationAccessInfoDTO to provide access to the plugin generated
 * serializer and also use with @Serializable(with=).
 *
 * Can be removed once https://github.com/Kotlin/kotlinx.serialization/issues/1169 is resolved
 * or when the API V3 is the minimum supported version, see [ConversationAccessInfoDTO].
 */
@Serializable
@SerialName("ConversationAccessInfoDTO")
@OptIn(ExperimentalSerializationApi::class)
private data class ConversationAccessInfoDTOSurrogate constructor(
    @SerialName("access")
    val access: Set<ConversationAccessDTO>,
    @SerialName("access_role_v2") @JsonNames("access_role")
    val accessRole: Set<ConversationAccessRoleDTO> = ConversationAccessRoleDTO.DEFAULT_VALUE_WHEN_NULL
)
