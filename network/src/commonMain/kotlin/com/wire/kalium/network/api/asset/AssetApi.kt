package com.wire.kalium.network.api.asset

import com.wire.kalium.network.utils.NetworkResponse
import okio.Source

interface AssetApi {

    suspend fun downloadAsset(assetKey: String, assetToken: String?): NetworkResponse<ByteArray>
    suspend fun downloadAsset(assetKey: String, assetKeyDomain: String, assetToken: String?): NetworkResponse<Source>

    suspend fun uploadAsset(metadata: AssetMetadataRequest, encryptedData: ByteArray): NetworkResponse<AssetResponse>
}
