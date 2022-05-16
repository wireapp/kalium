package com.wire.kalium.network.api.asset

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.utils.io.charsets.Charsets.UTF_8
import io.ktor.utils.io.core.toByteArray

class AssetApiImpl internal constructor(private val authenticatedNetworkClient: AuthenticatedNetworkClient) : AssetApi {

    private val httpClient get() = authenticatedNetworkClient.httpClient

    /**
     * Downloads an asset
     * @param assetKey the asset identifier
     * @param assetToken the asset token, can be null in case of public assets
     */
    override suspend fun downloadAsset(assetKey: String, assetToken: String?): NetworkResponse<ByteArray> = wrapKaliumResponse {
        httpClient.get("$PATH_PUBLIC_ASSETS/$assetKey") {
            assetToken?.let { header(HEADER_ASSET_TOKEN, it) }
        }
    }

    /** Uploads an already encrypted asset
     * @param metadata the metadata associated to the asset that wants to be uploaded
     * @param encryptedData the encrypted data on a ByteArray shape
     */
    override suspend fun uploadAsset(metadata: AssetMetadataRequest, encryptedData: ByteArray): NetworkResponse<AssetResponse> =
        wrapKaliumResponse {
            httpClient.post(PATH_PUBLIC_ASSETS) {
                contentType(ContentType.MultiPart.Mixed)
                setBody(provideAssetRequestBody(metadata, encryptedData))
            }
        }

    private fun provideAssetRequestBody(metadata: AssetMetadataRequest, encryptedData: ByteArray): ByteArray {
        val body = StringBuilder()

        // Part 1
        val strMetadata = "{\"public\": ${metadata.public}, \"retention\": \"${metadata.retentionType.name.lowercase()}\"}"

        body.append("--frontier\r\n")
        body.append("Content-Type: application/json;charset=utf-8\r\n")
        body.append("Content-Length: ")
            .append(strMetadata.length)
            .append("\r\n\r\n")
        body.append(strMetadata)
            .append("\r\n")

        // Part 2
        body.append("--frontier\r\n")
        body.append("Content-Type: application/octet-stream")
            .append("\r\n")
        body.append("Content-Length: ")
            .append(encryptedData.size)
            .append("\r\n")
        body.append("Content-MD5: ")
            .append(metadata.md5)
            .append("\r\n\r\n")

        val bodyArray = body.toString().toByteArray(UTF_8)
        val closingArray = "\r\n--frontier--\r\n".toByteArray(UTF_8)

        // Merge all sections on the request
        return bodyArray + encryptedData + closingArray
    }

    private companion object {
        const val PATH_PUBLIC_ASSETS = "/assets/v3"
        const val HEADER_ASSET_TOKEN = "Asset-Token"
    }
}
