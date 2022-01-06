package com.wire.kalium.network.api.asset

import com.wire.kalium.network.api.model.Asset
import com.wire.kalium.network.api.model.AssetMetadata
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.charsets.Charsets.UTF_8
import io.ktor.utils.io.core.toByteArray

class AssetApiImp(private val httpClient: HttpClient) : AssetApi {

    /**
     * download an asset
     * @param assetKey the asset identifier
     * @param assetToken the asset token, can be null in case of public assets
     */
    override suspend fun downloadAsset(assetKey: String, assetToken: String?): NetworkResponse<ByteArray> = wrapKaliumResponse {
        httpClient.get("$PATH_PUBLIC_ASSETS/$assetKey") {
            assetToken?.let { header(HEADER_ASSET_TOKEN, it) }
        }
    }

    override suspend fun uploadAsset(metadata: AssetMetadata, encryptedData: ByteArray): NetworkResponse<Asset> = wrapKaliumResponse {
        httpClient.post(PATH_PUBLIC_ASSETS) {
            contentType(ContentType.MultiPart.Mixed)
            setBody(provideAssetRequestBody(metadata, encryptedData))
        }
    }

    private fun provideAssetRequestBody(metadata: AssetMetadata, encryptedData: ByteArray): ByteArray {
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
        const val PATH_PUBLIC_ASSETS = "/assets/v3/"
        const val HEADER_ASSET_TOKEN = "Asset-Token"
    }
}
