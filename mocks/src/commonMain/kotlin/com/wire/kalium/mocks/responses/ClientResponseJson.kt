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

package com.wire.kalium.mocks.responses

import com.wire.kalium.network.api.authenticated.client.ClientCapabilityDTO
import com.wire.kalium.network.api.authenticated.client.ClientDTO
import com.wire.kalium.network.api.authenticated.client.ClientTypeDTO
import com.wire.kalium.network.api.authenticated.client.DeviceTypeDTO
import com.wire.kalium.network.tools.KtxSerializer
import kotlinx.serialization.encodeToString

object ClientResponseJson {
    private val jsonProvider = { serializable: ClientDTO ->
        """
        |{
        |   "id": "${serializable.clientId}",
        |   "type": "${serializable.type}",
        |   "time": "${serializable.registrationTime}",
        |   "last_active": "${serializable.lastActive}",
        |   "class": "${serializable.deviceType}",
        |   "label": "${serializable.label}",
        |   "cookie": "${serializable.cookie}",
        |   "model": "${serializable.model}",
        |   "capabilities": [
        |        ${KtxSerializer.json.encodeToString(serializable.capabilities[0])}
        |   ],
        |  "mls_public_keys": ${serializable.mlsPublicKeys}
        |}
        """.trimMargin()
    }

    // This is backwards compatible with the old format till v5 API get deprecated
    private val jsonProviderCapabilitiesObject = { serializable: ClientDTO ->
        """
        |{
        |   "id": "${serializable.clientId}",
        |   "type": "${serializable.type}",
        |   "time": "${serializable.registrationTime}",
        |   "last_active": "${serializable.lastActive}",
        |   "class": "${serializable.deviceType}",
        |   "label": "${serializable.label}",
        |   "cookie": "${serializable.cookie}",
        |   "model": "${serializable.model}",
        |   "capabilities": {
        |     "capabilities": [
        |        ${KtxSerializer.json.encodeToString(serializable.capabilities[0])}
        |     ]
        |  },
        |  "mls_public_keys": ${serializable.mlsPublicKeys}
        |}
        """.trimMargin()
    }

    val validCapabilitiesObject = ValidJsonProvider(
        ClientDTO(
            clientId = "defkrr8e7grgsoufhg8",
            type = ClientTypeDTO.Permanent,
            deviceType = DeviceTypeDTO.Phone,
            registrationTime = "2021-05-12T10:52:02.671Z",
            lastActive = "2021-05-12T10:52:02.671Z",
            label = "label",
            cookie = "sldkfmdeklmwldwlek23kl44mntiuepfojfndkjd",
            capabilities = listOf(ClientCapabilityDTO.LegalHoldImplicitConsent),
            model = "model",
            mlsPublicKeys = null
        ),
        jsonProviderCapabilitiesObject
    )

    val valid = ValidJsonProvider(
        ClientDTO(
            clientId = "defkrr8e7grgsoufhg8",
            type = ClientTypeDTO.Permanent,
            deviceType = DeviceTypeDTO.Phone,
            registrationTime = "2021-05-12T10:52:02.671Z",
            lastActive = "2021-05-12T10:52:02.671Z",
            label = "label",
            cookie = "sldkfmdeklmwldwlek23kl44mntiuepfojfndkjd",
            capabilities = listOf(ClientCapabilityDTO.LegalHoldImplicitConsent),
            model = "model",
            mlsPublicKeys = null
        ),
        jsonProvider
    )

    fun createValid(client: ClientDTO) = ValidJsonProvider(client, jsonProvider)
}
