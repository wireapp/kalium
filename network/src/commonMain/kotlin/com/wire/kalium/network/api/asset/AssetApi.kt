package com.wire.kalium.network.api.asset

import com.wire.kalium.network.api.KaliumHttpResult

interface AssetApi {

    suspend fun downloadAsset(assetKey: String, assetToken: String?): KaliumHttpResult<ByteArray>

    // Signature still to be defined once there is proper documentation in place
    suspend fun uploadAsset()
}
