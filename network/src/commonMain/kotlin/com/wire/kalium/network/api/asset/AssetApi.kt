package com.wire.kalium.network.api.asset

import com.wire.kalium.network.utils.NetworkResponse

interface AssetApi {

    suspend fun downloadAsset(assetKey: String, assetToken: String?): NetworkResponse<ByteArray>

    // Signature still to be defined once there is proper documentation in place
    suspend fun uploadAsset()
}
