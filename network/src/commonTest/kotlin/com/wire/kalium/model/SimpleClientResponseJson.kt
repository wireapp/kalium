package com.wire.kalium.model

import com.wire.kalium.api.json.ValidJsonProvider
import com.wire.kalium.network.api.base.authenticated.client.DeviceTypeDTO
import com.wire.kalium.network.api.base.authenticated.client.SimpleClientResponse

object SimpleClientResponseJson {
    private val missingClassJsonProvider = { serializable: SimpleClientResponse ->
        """
        |{
        |   "id": "${serializable.id}"
        |}
        """.trimMargin()
    }
    private val gibberishClassJsonProvider = { serializable: SimpleClientResponse ->
        """
        |{
        |   "id": "${serializable.id}",
        |   "class": "198237juf9"
        |}
        """.trimMargin()
    }
    private val jsonProvider = { serializable: SimpleClientResponse ->
        """
        |{
        |   "id": "${serializable.id}",
        |   "class": "${serializable.deviceClass}"
        |}
        """.trimMargin()
    }

    val valid = ValidJsonProvider(
        SimpleClientResponse(
            id = "3b3a54c770f5e1a4",
            deviceClass = DeviceTypeDTO.Phone
        ),
        jsonProvider
    )

    val validMissingClass = ValidJsonProvider(
        SimpleClientResponse(
            id = "3b3a54c770f5e1a4"
        ),
        missingClassJsonProvider
    )

    val validGibberishClass = ValidJsonProvider(
        SimpleClientResponse(
            id = "3b3a54c770f5e1a4"
        ),
        gibberishClassJsonProvider
    )

    fun createValid(clientResponse: SimpleClientResponse) = ValidJsonProvider(clientResponse, jsonProvider)
}
