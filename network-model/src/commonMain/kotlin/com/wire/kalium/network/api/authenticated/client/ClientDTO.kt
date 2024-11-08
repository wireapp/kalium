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

package com.wire.kalium.network.api.authenticated.client

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer

@Serializable
data class ClientDTO(
    @SerialName("cookie") val cookie: String?,
    @SerialName("time") val registrationTime: String, // yyyy-mm-ddThh:MM:ss.qqq
    @SerialName("last_active") val lastActive: String?, // yyyy-mm-ddThh:MM:ss.qqq
    @SerialName("model") val model: String?,
    @SerialName("id") val clientId: String,
    @SerialName("type") val type: ClientTypeDTO,
    @SerialName("class") val deviceType: DeviceTypeDTO = DeviceTypeDTO.Unknown,
    @SerialName("capabilities")
    @Serializable(with = CapabilitiesDeserializer::class)
    val capabilities: List<ClientCapabilityDTO>,
    @SerialName("label") val label: String?,
    @SerialName("mls_public_keys") val mlsPublicKeys: Map<String, String>?
)

/**
 * Sometimes the capabilities are wrapped in an object, sometimes they are just an array.
 * See [documentation](https://wearezeta.atlassian.net/wiki/spaces/ENGINEERIN/pages/1309868033/API+changes+v6+v7)
 */
object CapabilitiesDeserializer :
    JsonTransformingSerializer<List<ClientCapabilityDTO>>(ListSerializer(ClientCapabilityDTO.serializer())) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        return when {
            element is JsonObject && element.containsKey("capabilities") -> element["capabilities"]!!
            element is JsonArray -> element
            else -> throw SerializationException("Unexpected JSON format for capabilities")
        }
    }
}

@Serializable
data class ClientsOfUsersResponse(
    @SerialName("qualified_user_map") val qualifiedMap: Map<String, Map<String, List<SimpleClientResponse>>>
)

@Serializable
data class SimpleClientResponse(
    @SerialName("id") val id: String,
    @SerialName("class") val deviceClass: DeviceTypeDTO = DeviceTypeDTO.Unknown
)
