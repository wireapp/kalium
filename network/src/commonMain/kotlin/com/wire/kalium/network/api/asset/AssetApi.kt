package com.wire.kalium.network.api.asset

import com.wire.kalium.network.api.model.AssetResponse
import com.wire.kalium.network.api.model.AssetMetadataRequest
import com.wire.kalium.network.utils.NetworkResponse

interface AssetApi {

    suspend fun downloadAsset(assetKey: String, assetToken: String?): NetworkResponse<ByteArray>

    suspend fun uploadAsset(metadata: AssetMetadataRequest, encryptedData: ByteArray): NetworkResponse<AssetResponse>
}
