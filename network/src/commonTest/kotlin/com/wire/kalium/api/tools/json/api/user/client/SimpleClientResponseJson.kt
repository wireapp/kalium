package com.wire.kalium.api.tools.json.api.user.client

import com.wire.kalium.api.tools.json.ValidJsonProvider
import com.wire.kalium.network.api.user.client.ClientResponse
import com.wire.kalium.network.api.user.client.SimpleClientResponse

object SimpleClientResponseJson {
    private val missingClassJsonProvider = { serializable: SimpleClientResponse ->
        """
        |{
        |   "id": "${serializable.id}"
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
            deviceClass = "desktop"
        ),
        jsonProvider
    )

    val validMissingClass = ValidJsonProvider(
        SimpleClientResponse(
            id = "3b3a54c770f5e1a4"
        ),
        missingClassJsonProvider
    )

    fun createValid(clientResponse: SimpleClientResponse) = ValidJsonProvider(clientResponse, jsonProvider)
}
