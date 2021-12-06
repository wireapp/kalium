package com.wire.kalium.api.tools.json.model

import com.wire.kalium.api.tools.json.ValidJsonProvider
import com.wire.kalium.network.api.ErrorResponse

object ErrorResponseJson {
    private val jsonProvider = { serializable: ErrorResponse ->
        """
        |{
        |  "code": ${serializable.code},
        |  "label": "${serializable.label}",
        |  "message": "${serializable.message}",
        |}
        """.trimMargin()
    }

    val valid = ValidJsonProvider(
        ErrorResponse(code = 499, label = "error_label", message = "error_message"),
        jsonProvider
    )
}
