package com.wire.kalium.network.api.asset

import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.*
import io.ktor.client.request.*

class AssetApiImp(private val httpClient: HttpClient) : AssetApi {

    /**
     * download an asset
     * @param assetKey the asset identifier
     * @param assetToken the asset token, can be null in case of public assets
     */
    override suspend fun downloadAsset(assetKey: String, assetToken: String?): NetworkResponse<ByteArray> = wrapKaliumResponse {
        httpClient.get(path = "$PATH_PUBLIC_ASSETS/$assetKey") {
            assetToken?.let { header(HEADER_ASSET_TOKEN, it) }
        }
    }

    override suspend fun uploadAsset() {
        TODO("not yet implemented")
    }

    private companion object {
        const val PATH_PUBLIC_ASSETS = "/assets/v3/"
        const val HEADER_ASSET_TOKEN = "Asset-Token"
    }
}
