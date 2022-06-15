package com.wire.kalium.network.api.asset

import com.wire.kalium.network.api.AssetId
import com.wire.kalium.network.utils.NetworkResponse

interface AssetApi {

    suspend fun downloadAsset(assetId: AssetId, assetToken: String?): NetworkResponse<ByteArray>

    suspend fun uploadAsset(metadata: AssetMetadataRequest, encryptedData: ByteArray): NetworkResponse<AssetResponse>
}
