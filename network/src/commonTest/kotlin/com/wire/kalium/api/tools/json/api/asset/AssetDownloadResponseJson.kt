package com.wire.kalium.api.tools.json.api.asset

import com.wire.kalium.api.tools.json.ValidJsonProvider
import com.wire.kalium.network.api.ErrorResponse

object AssetDownloadResponseJson {
    private val invalidJsonProvider = { serializable: ErrorResponse ->
        """
        |{
        |   "code": "${serializable.code}",
        |   "message": "${serializable.message}",
        |   "label": "${serializable.label}"
        |}
        """.trimMargin()
    }

    val invalid = ValidJsonProvider(
        ErrorResponse(401, "Invalid Asset Token", "invalid_asset_token"),
        invalidJsonProvider
    )
}
