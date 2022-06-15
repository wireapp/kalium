package com.wire.kalium.network.api.asset

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.charsets.Charsets.UTF_8
import io.ktor.utils.io.core.toByteArray
import okio.Source
import okio.buffer

interface AssetApi {
    suspend fun downloadAsset(assetKey: String, assetKeyDomain: String, assetToken: String?): NetworkResponse<Source>
    suspend fun uploadAsset(
        metadata: AssetMetadataRequest,
        encryptedDataSource: Source,
        encryptedDataSize: Long
    ): NetworkResponse<AssetResponse>
}

class AssetApiImpl internal constructor(
    private val authenticatedNetworkClient: AuthenticatedNetworkClient
) : AssetApi {

    private val httpClient get() = authenticatedNetworkClient.httpClient

    /**
     * Downloads an asset
     * @param assetKey the asset identifier
     * @param assetKeyDomain the domain of the asset identifier
     * @param assetToken the asset token, can be null in case of public assets
     */
    override suspend fun downloadAsset(assetKey: String, assetKeyDomain: String, assetToken: String?): NetworkResponse<Source> =
        wrapKaliumResponse {
            httpClient.get("$PATH_PUBLIC_ASSETS/$assetKeyDomain/$assetKey") {
                assetToken?.let { header(HEADER_ASSET_TOKEN, it) }
            }
        }

    /** Uploads an already encrypted asset
     * @param metadata the metadata associated to the asset that wants to be uploaded
     * @param encryptedDataSource the source of the encrypted data to be uploaded
     * @param encryptedDataSize the size in bytes of the asset to be uploaded
     */
    override suspend fun uploadAsset(
        metadata: AssetMetadataRequest,
        encryptedDataSource: Source,
        encryptedDataSize: Long
    ): NetworkResponse<AssetResponse> =
        wrapKaliumResponse {
            httpClient.post(PATH_PUBLIC_ASSETS) {
                contentType(ContentType.MultiPart.Mixed)
                setBody(StreamAssetContent(metadata, encryptedDataSize, encryptedDataSource))
            }
        }

    private companion object {
        const val PATH_PUBLIC_ASSETS = "/assets/v3"
        const val HEADER_ASSET_TOKEN = "Asset-Token"
    }
}

class StreamAssetContent(
    private val metadata: AssetMetadataRequest,
    private val encryptedDataSize: Long,
    private val fileContentStream: Source
) : OutgoingContent.WriteChannelContent() {
    private val openingData: ByteArray by lazy {
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
            .append(encryptedDataSize)
            .append("\r\n")
        body.append("Content-MD5: ")
            .append(metadata.md5)
            .append("\r\n\r\n")

        body.toString().toByteArray(UTF_8)
    }

    private val closingArray = "\r\n--frontier--\r\n".toByteArray(UTF_8)

    override suspend fun writeTo(channel: ByteWriteChannel) {
        channel.writeFully(openingData, 0, openingData.size)

        val stream = fileContentStream.buffer()
        while (true) {
            val byteArray = stream.readByteArray()
            if (byteArray.isEmpty()) {
                break
            } else {
                channel.writeFully(byteArray, 0, byteArray.size)
                channel.flush()
            }
        }

        channel.writeFully(closingArray, 0, closingArray.size)
    }
}
