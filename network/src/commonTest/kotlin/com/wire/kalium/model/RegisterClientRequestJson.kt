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

package com.wire.kalium.model

import com.wire.kalium.api.json.FaultyJsonProvider
import com.wire.kalium.api.json.ValidJsonProvider
import com.wire.kalium.network.api.base.authenticated.client.ClientCapabilityDTO
import com.wire.kalium.network.api.base.authenticated.client.ClientTypeDTO
import com.wire.kalium.network.api.base.authenticated.client.DeviceTypeDTO
import com.wire.kalium.network.api.base.authenticated.client.RegisterClientRequest
import com.wire.kalium.network.api.base.authenticated.prekey.PreKeyDTO
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

object RegisterClientRequestJson {

    private val jsonProvider = { serializable: RegisterClientRequest ->
        buildJsonObject {
            // FIXME: Data is being created without being added to the json object
            //        Doing "key" to "value" does not add the key to the json object.
            //        We need to call put("key", "value") instead.
            putJsonArray("prekeys") {
                serializable.preKeys.forEach {
                    addJsonObject {
                        "id" to it.id
                        "key" to it.key
                    }
                }
            }
            putJsonObject("lastkey") {
                "id" to serializable.lastKey.id
                "key" to serializable.lastKey.key
            }
            "type" to serializable.type
            serializable.password?.let { "password" to it }
            serializable.deviceType?.let { "class" to it }
            serializable.label?.let { "label" to it }
            serializable.model?.let { "model" to it }
            serializable.cookieLabel?.let { "cookie" to it }
            serializable.capabilities?.let {
                putJsonArray("capabilities") {
                    it.forEach { clientCapabilityDTO ->
                        add(clientCapabilityDTO.toString())
                    }
                }
            }
            serializable.secondFactorVerificationCode?.let {
                put("verification_code", it)
            }
        }.toString()
    }

    val valid = ValidJsonProvider(
        RegisterClientRequest(
            password = "password",
            deviceType = DeviceTypeDTO.Desktop,
            type = ClientTypeDTO.Permanent,
            label = "label",
            preKeys = listOf(PreKeyDTO(1, "preykey_1"), PreKeyDTO(2, "prekey_2")),
            lastKey = PreKeyDTO(999, "last_prekey"),
            capabilities = listOf(ClientCapabilityDTO.LegalHoldImplicitConsent),
            model = "model",
            cookieLabel = "cookie label",
            secondFactorVerificationCode = "123456"
        ),
        jsonProvider
    )

    val missingDeviceType = FaultyJsonProvider(
        """
        |{
        |  "label": "label",
        |  "lastkey": {
        |       "key": "last_prekey",
        |       "id": 9999
        |  },
        |  "password": "password",
        |  "prekeys": [
        |       {
        |           "id": 1,
        |           "key": "prekey_1"
        |       },
        |       {
        |           "id": 2,
        |           "key": "prekey_2""
        |       }
        |  ],
        |  "type": "permanent",
        |  "capabilities": [
        |    "legalhold-implicit-consent"
        |    ],
        |  "model": "model"
        |}
        """.trimMargin()
    )

    val missingLabel = FaultyJsonProvider(
        """
        |{
        |  "class": "desktop",
        |  "lastkey": {
        |       "key": "last_prekey",
        |       "id": 9999
        |  },
        |  "password": "password",
        |  "prekeys": [
        |       {
        |           "id": 1,
        |           "key": "prekey_1"
        |       },
        |       {
        |           "id": 2,
        |           "key": "prekey_2""
        |       }
        |  ],
        |  "type": "desktop",
        |  "capabilities": [
        |    "legalhold-implicit-consent"
        |    ],
        |  "model": "model"
        |}
        """.trimMargin()
    )

    val missingPassword = FaultyJsonProvider(
        """
        |{
        |  "class": "desktop",
        |  "label": "label",
        |  "lastkey": {
        |       "key": "last_prekey",
        |       "id": 9999
        |  },
        |  "prekeys": [
        |       {
        |           "id": 1,
        |           "key": "prekey_1"
        |       },
        |       {
        |           "id": 2,
        |           "key": "prekey_2""
        |       }
        |  ],
        |  "type": "desktop",
        |  "capabilities": [
        |    "legalhold-implicit-consent"
        |    ],
        |  "model": "model"
        |}
        """.trimMargin()
    )

    val missingTye = FaultyJsonProvider(
        """
        |{
        |  "class": "desktop",
        |  "label": "label",
        |  "lastkey": {
        |       "key": "last_prekey",
        |       "id": 9999
        |  },
        |  "password": "password",
        |  "prekeys": [
        |       {
        |           "id": 1,
        |           "key": "prekey_1"
        |       },
        |       {
        |           "id": 2,
        |           "key": "prekey_2""
        |       }
        |  ],
        |  "capabilities": [
        |    "legalhold-implicit-consent"
        |    ],
        |  "model": "model"
        |}
        """.trimMargin()
    )

    val missingPreKeys = FaultyJsonProvider(
        """
        |{
        |  "class": "desktop",
        |  "label": "label",
        |  "lastkey": {
        |       "key": "last_prekey",
        |       "id": 9999
        |  },
        |  "password": "password",
        |   "type": "desktop",
        |  "capabilities": [
        |    "legalhold-implicit-consent"
        |    ],
        |  "model": "model"
        |}
        """.trimMargin()
    )

    val missingLastKey = FaultyJsonProvider(
        """
        |{
        |  "class": "desktop",
        |  "label": "label",
        |  "password": "password",
        |  "prekeys": [
        |       {
        |           "id": 1,
        |           "key": "prekey_1"
        |       },
        |       {
        |           "id": 2,
        |           "key": "prekey_2""
        |       }
        |  ],
        |  "type": "desktop",
        |  "capabilities": [
        |    "legalhold-implicit-consent"
        |    ],
        |  "model": "model"
        |}
        """.trimMargin()
    )

    val missingCapabilities = FaultyJsonProvider(
        """
        |{
        |  "class": "desktop",
        |  "label": "label",
        |  "lastkey": {
        |       "key": "last_prekey",
        |       "id": 9999
        |  },
        |  "password": "password",
        |  "prekeys": [
        |       {
        |           "id": 1,
        |           "key": "prekey_1"
        |       },
        |       {
        |           "id": 2,
        |           "key": "prekey_2""
        |       }
        |  ],
        |  "type": "permanent",
        |  "model": "model"
        |}
        """.trimMargin()
    )

    val missingModel = FaultyJsonProvider(
        """
        |{
        |  "class": "desktop",
        |  "label": "label",
        |  "lastkey": {
        |       "key": "last_prekey",
        |       "id": 9999
        |  },
        |  "password": "password",
        |  "prekeys": [
        |       {
        |           "id": 1,
        |           "key": "prekey_1"
        |       },
        |       {
        |           "id": 2,
        |           "key": "prekey_2""
        |       }
        |  ],
        |  "type": "permanent",
        |  "capabilities": [
        |    "legalhold-implicit-consent"
        |    ]
        |}
        """.trimMargin()
    )
}
