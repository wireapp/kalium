package com.wire.kalium.api.tools.json.api.user.client

import com.wire.kalium.api.tools.json.FaultyJsonProvider
import com.wire.kalium.api.tools.json.ValidJsonProvider
import com.wire.kalium.network.api.prekey.PreKeyDTO
import com.wire.kalium.network.api.user.client.ClientCapabilityDTO
import com.wire.kalium.network.api.user.client.ClientTypeDTO
import com.wire.kalium.network.api.user.client.DeviceTypeDTO
import com.wire.kalium.network.api.user.client.RegisterClientRequest
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

object RegisterClientRequestJson {

    private val jsonProvider = { serializable: RegisterClientRequest ->
        buildJsonObject {
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
            serializable.capabilities?.let {
                putJsonArray("capabilities") {
                    it.forEach { clientCapabilityDTO ->
                        add(clientCapabilityDTO.toString())
                    }
                }
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
            model = "model"
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
