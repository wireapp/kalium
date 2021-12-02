package com.wire.kalium.api.asset

import com.wire.kalium.api.KaliumHttpResult
import com.wire.kalium.api.wrapKaliumResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*

class AssetApiImp(private val httpClient: HttpClient) : AssetApi {

    /**
     * download an asset
     * @param assetKey the asset identifier
     * @param assetToken the asset token, can be null in case of public assets
     */
    override suspend fun downloadAsset(assetKey: String, assetToken: String?): KaliumHttpResult<ByteArray> = wrapKaliumResponse {
        httpClient.get<HttpResponse>(path = "$PATH_PUBLIC_ASSETS$assetKey") {
            assetToken?.let { header(HEADER_ASSET_TOKEN, it) }
        }.receive()
    }

    override suspend fun uploadAsset() {
        TODO ("not yet implemented")
    }

    private companion object {
        const val PATH_PUBLIC_ASSETS = "/assets/v3/"
        const val HEADER_ASSET_TOKEN = "Asset-Token"
    }
}
