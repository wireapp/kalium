package com.wire.kalium.api.assets

import com.wire.kalium.api.KaliumHttpResult
import io.ktor.client.statement.*

interface AssetsApi {

    suspend fun downloadAsset(assetKey: String, assetToken: String?): KaliumHttpResult<HttpResponse>

    // Signature still to be defined once there is proper documentation in place
    suspend fun uploadAsset(): KaliumHttpResult<HttpResponse>
}
