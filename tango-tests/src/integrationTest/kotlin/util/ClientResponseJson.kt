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

package util

import com.wire.kalium.network.api.base.authenticated.client.Capabilities
import com.wire.kalium.network.api.base.authenticated.client.ClientCapabilityDTO
import com.wire.kalium.network.api.base.authenticated.client.ClientDTO
import com.wire.kalium.network.api.base.authenticated.client.ClientTypeDTO
import com.wire.kalium.network.api.base.authenticated.client.DeviceTypeDTO

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
        |   "capabilities": {
        |     "capabilities": [
        |        "${serializable.capabilities!!.capabilities[0]}"
        |     ]
        |  }
        |}
        """.trimMargin()
    }

    val valid = ValidJsonProvider(
        ClientDTO(
            clientId = "defkrr8e7grgsoufhg8",
            type = ClientTypeDTO.Permanent,
            deviceType = DeviceTypeDTO.Phone,
            registrationTime = "2023-05-12T10:52:02.671Z",
            lastActive = "2023-05-12T10:52:02.671Z",
            label = "label",
            cookie = "sldkfmdeklmwldwlek23kl44mntiuepfojfndkjd",
            capabilities = Capabilities(listOf(ClientCapabilityDTO.LegalHoldImplicitConsent)),
            model = "model",
            mlsPublicKeys = null
        ),
        jsonProvider
    )

    fun createValid(client: ClientDTO) = ValidJsonProvider(client, jsonProvider)
}
