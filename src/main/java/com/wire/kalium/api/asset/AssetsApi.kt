package com.wire.kalium.api.asset

import com.wire.kalium.api.KaliumHttpResult
import io.ktor.client.statement.*

interface AssetsApi {

    suspend fun downloadAsset(assetKey: String, assetToken: String?): KaliumHttpResult<ByteArray>

    // Signature still to be defined once there is proper documentation in place
    suspend fun uploadAsset(): KaliumHttpResult<HttpResponse>
}
