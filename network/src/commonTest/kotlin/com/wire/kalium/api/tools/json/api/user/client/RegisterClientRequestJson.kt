package com.wire.kalium.api.tools.json.api.user.client

import com.wire.kalium.api.tools.json.FaultyJsonProvider
import com.wire.kalium.api.tools.json.ValidJsonProvider
import com.wire.kalium.network.api.prekey.PreKeyDTO
import com.wire.kalium.network.api.user.client.ClientCapabilityDTO
import com.wire.kalium.network.api.user.client.ClientTypeDTO
import com.wire.kalium.network.api.user.client.DeviceTypeDTO
import com.wire.kalium.network.api.user.client.RegisterClientRequest

object RegisterClientRequestJson {

    private val jsonProvider = { serializable: RegisterClientRequest ->
        """
        |{
        |  "class": ${serializable.deviceType.name},
        |  "label": "${serializable.label}",
        |  "lastkey": {
        |       "key": "${serializable.lastKey.key}",
        |       "id": ${serializable.lastKey.id}
        |  },
        |  "password": "${serializable.password}",
        |  "prekeys": [
        |       {
        |           "id": ${serializable.preKeyDTOS[0].id},
        |           "key": "${serializable.preKeyDTOS[0].key}"
        |       },
        |       {
        |           "id": ${serializable.preKeyDTOS[1].id},
        |           "key": "${serializable.preKeyDTOS[1].key}"
        |       }
        |  ],
        |  "type": "${serializable.type.name}",
        |  "capabilities": [
        |       "${serializable.capabilities?.get(0)}"
        |  ],
        |  "model": "${serializable.model}"
        |}
        """.trimMargin()
    }

    val valid = ValidJsonProvider(
        RegisterClientRequest(
            password = "password",
            deviceType = DeviceTypeDTO.Desktop,
            type = ClientTypeDTO.Permanent,
            label = "label",
            preKeyDTOS = listOf(PreKeyDTO(1, "preykey_1"), PreKeyDTO(2, "prekey_2")),
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
