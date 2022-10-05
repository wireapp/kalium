package com.wire.kalium.model.asset

import com.wire.kalium.api.json.ValidJsonProvider
import com.wire.kalium.network.api.base.model.ErrorResponse

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
