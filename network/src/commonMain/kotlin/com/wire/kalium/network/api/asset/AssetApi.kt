package com.wire.kalium.network.api.asset

import com.wire.kalium.network.api.model.Asset
import com.wire.kalium.network.api.model.AssetMetadata
import com.wire.kalium.network.utils.NetworkResponse

interface AssetApi {

    suspend fun downloadAsset(assetKey: String, assetToken: String?): NetworkResponse<ByteArray>

    suspend fun uploadAsset(metadata: AssetMetadata, encryptedData: ByteArray): NetworkResponse<Asset>
}
