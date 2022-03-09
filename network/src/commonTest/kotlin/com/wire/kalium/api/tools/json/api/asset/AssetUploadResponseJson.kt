package com.wire.kalium.api.tools.json.api.asset

import com.wire.kalium.api.tools.json.ValidJsonProvider
import com.wire.kalium.network.api.ErrorResponse
import com.wire.kalium.network.api.asset.AssetResponse

object AssetUploadResponseJson {
    private val validJsonProvider = { serializable: AssetResponse ->
        """
        |{
        |   "key": "${serializable.key}",
        |   "expires": "${serializable.expires}",
        |   "token": "${serializable.token}",
        |   "domain": "${serializable.domain}"
        |}
        """.trimMargin()
    }
    private val invalidJsonProvider = { serializable: ErrorResponse ->
        """
        |{
        |   "code": "${serializable.code}",
        |   "message": "${serializable.message}",
        |   "label": "${serializable.label}"
        |}
        """.trimMargin()
    }

    val valid = ValidJsonProvider(
        AssetResponse(
            key = "3-1-e7788668-1b22-488a-b63c-acede42f771f",
            expires = "expiration_date",
            token = "asset_token",
            domain = "staging.wire.link"
        ),
        validJsonProvider
    )

    val invalid = ValidJsonProvider(
        ErrorResponse(401, "Invalid Asset Token", "invalid_asset_token"),
        invalidJsonProvider
    )
}
