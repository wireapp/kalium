package com.wire.kalium.model

import com.wire.kalium.api.json.ValidJsonProvider
import com.wire.kalium.network.api.base.authenticated.message.SendMLSMessageResponse

object SendMLSMessageResponseJson {

    private const val TIME = "2021-05-31T10:52:02.671Z"

    private val emptyResponse = { response: SendMLSMessageResponse ->
        """
        |{
        |   "time": "${response.time}"
        |   "events" : []
        |}
        """.trimMargin()
    }

    val validMessageSentJson = ValidJsonProvider(
        SendMLSMessageResponse(TIME, emptyList()),
        emptyResponse
    )
}
