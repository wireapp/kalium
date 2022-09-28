package com.wire.kalium.api.json.model

import com.wire.kalium.api.json.ValidJsonProvider
import com.wire.kalium.network.api.base.model.ErrorResponse

object ErrorResponseJson {
    private val jsonProvider = { serializable: ErrorResponse ->
        """
        |{
        |  "code": ${serializable.code},
        |  "label": "${serializable.label}",
        |  "message": "${serializable.message}"
        |}
        """.trimMargin()
    }

    val valid = ValidJsonProvider(
        ErrorResponse(code = 499, label = "error_label", message = "error_message"),
        jsonProvider
    )
}
