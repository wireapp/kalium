package com.wire.kalium.api.asset

import com.wire.kalium.api.KaliumHttpResult
import com.wire.kalium.api.wrapKaliumResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import java.io.InputStream

class AssetsApiImp(private val httpClient: HttpClient) : AssetsApi {

    override suspend fun downloadAsset(assetKey: String, assetToken: String?): KaliumHttpResult<ByteArray> = wrapKaliumResponse {
        httpClient.get<HttpResponse>(path = "$PATH_PUBLIC_ASSETS$assetKey") {
            assetToken?.run {
                header(HEADER_ASSET_TOKEN, this)
            }
        }.receive()
    }

    override suspend fun uploadAsset(): KaliumHttpResult<HttpResponse> = wrapKaliumResponse {
        httpClient.get<HttpResponse>(path = PATH_PUBLIC_ASSETS) {
            // Method signature and Implementation still to be defined once there is proper documentation in place in Swagger
        }.receive()
    }

    private companion object {
        const val PATH_PUBLIC_ASSETS = "/assets/v3/"
        const val HEADER_ASSET_TOKEN = "Asset-Token"
    }
}
