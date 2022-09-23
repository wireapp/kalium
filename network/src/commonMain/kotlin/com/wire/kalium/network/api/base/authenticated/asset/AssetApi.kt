package com.wire.kalium.network.api.base.authenticated.asset

import com.wire.kalium.network.api.base.model.AssetId
import com.wire.kalium.network.utils.NetworkResponse
import okio.Sink
import okio.Source

interface AssetApi {
    /**
     * Downloads an asset, this will try to consume api v4 (federated aware endpoint)
     * @param assetId the asset identifier
     * @param assetToken the asset token, can be null in case of public assets
     * @return a [NetworkResponse] with a reference to an open Okio [Source] object from which one will be able to stream the data
     */
    suspend fun downloadAsset(assetId: AssetId, assetToken: String?, tempFileSink: Sink): NetworkResponse<Unit>

    /** Uploads an already encrypted asset
     * @param metadata the metadata associated to the asset that wants to be uploaded
     * @param encryptedDataSource the source of the encrypted data to be uploaded
     * @param encryptedDataSize the size in bytes of the asset to be uploaded
     */
    suspend fun uploadAsset(
        metadata: AssetMetadataRequest,
        encryptedDataSource: () -> Source,
        encryptedDataSize: Long
    ): NetworkResponse<AssetResponse>

    /**
     * Deletes an asset, this will try to consume api v4 (federated aware endpoint)
     * @param assetId the asset identifier
     * @param assetToken the asset token, can be null in case of public assets
     */
    suspend fun deleteAsset(assetId: AssetId, assetToken: String?): NetworkResponse<Unit>
}
