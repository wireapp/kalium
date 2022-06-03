package com.wire.kalium.api.tools.json.api.featureConfig

import com.wire.kalium.api.tools.json.ValidJsonProvider
import com.wire.kalium.api.tools.json.api.asset.AssetDownloadResponseJson
import com.wire.kalium.network.api.ErrorResponse
import com.wire.kalium.network.api.featureConfigs.FeatureConfigResponse

object FeatureConfigJson {
    val featureConfigResponseSerializer = { it: FeatureConfigResponse ->
        """
        |{
        |   "lockStatus": "${it.lockStatus}",
        |   "status": "${it.status}"
        |}
        """.trimMargin()
    }

    val featureConfigResponseSerializerResponse = ValidJsonProvider(
        FeatureConfigResponse("locked", "enabled"), featureConfigResponseSerializer
    )

    private val invalidJsonProvider = { serializable: ErrorResponse ->
        """
        |{
        |   "code": "${serializable.code}",
        |   "message": "${serializable.message}",
        |   "label": "${serializable.label}"
        |}
        """.trimMargin()
    }

    val insufficientPermissionsErrorResponse = ValidJsonProvider(
        ErrorResponse(403, "Insufficient permissions", "operation-denied"),
        invalidJsonProvider
    )

    val teamNotFoundErrorResponse = ValidJsonProvider(
        ErrorResponse(404, "Team not found", "no-team"),
        invalidJsonProvider
    )

}
