package com.wire.kalium.network.api.v2.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.base.authenticated.asset.AssetMetadataRequest
import com.wire.kalium.network.api.base.authenticated.asset.AssetResponse
import com.wire.kalium.network.api.base.model.AssetId
import com.wire.kalium.network.api.v0.authenticated.AssetApiV0
import com.wire.kalium.network.api.v0.authenticated.StreamAssetContent
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import okio.Source

internal open class AssetApiV2 internal constructor(
    private val authenticatedNetworkClient: AuthenticatedNetworkClient
) : AssetApiV0(authenticatedNetworkClient) {
    override fun buildAssetsPath(assetId: AssetId): String = "$PATH_ASSETS/${assetId.domain}/${assetId.value}"

    private val httpClient get() = authenticatedNetworkClient.httpClient

    override suspend fun uploadAsset(
        metadata: AssetMetadataRequest,
        encryptedDataSource: () -> Source,
        encryptedDataSize: Long
    ): NetworkResponse<AssetResponse> =
        wrapKaliumResponse {
            httpClient.post(PATH_ASSETS) {
                contentType(ContentType.MultiPart.Mixed)
                setBody(StreamAssetContent(metadata, encryptedDataSize, encryptedDataSource))
            }
        }

    private companion object {
        const val PATH_ASSETS = "assets"
    }
}
