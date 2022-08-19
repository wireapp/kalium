package com.wire.kalium.network.api.asset

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.AssetId
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.kaliumLogger
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.cancel
import io.ktor.utils.io.charsets.Charsets.UTF_8
import io.ktor.utils.io.close
import io.ktor.utils.io.core.isNotEmpty
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.core.toByteArray
import io.ktor.utils.io.read
import okio.Buffer
import okio.Sink
import okio.Source
import okio.buffer
import okio.use

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
        encryptedDataSource: Source,
        encryptedDataSize: Long
    ): NetworkResponse<AssetResponse>

    /**
     * Deletes an asset, this will try to consume api v4 (federated aware endpoint)
     * @param assetId the asset identifier
     * @param assetToken the asset token, can be null in case of public assets
     */
    suspend fun deleteAsset(assetId: AssetId, assetToken: String?): NetworkResponse<Unit>
}

class AssetApiImpl internal constructor(
    private val authenticatedNetworkClient: AuthenticatedNetworkClient
) : AssetApi {

    private val httpClient get() = authenticatedNetworkClient.httpClient

    override suspend fun downloadAsset(assetId: AssetId, assetToken: String?, tempFileSink: Sink): NetworkResponse<Unit> {
        return try {
            httpClient.prepareGet(buildAssetsPath(assetId)) {
                assetToken?.let { header(HEADER_ASSET_TOKEN, it) }
            }.execute { httpResponse ->
                val byteBufferSize = 1024 * 5
                val channel = httpResponse.body<ByteReadChannel>()
                tempFileSink.use { sink ->
                    while (!channel.isClosedForRead) {
                        val packet = channel.readRemaining(byteBufferSize.toLong(), 0)
                        while (packet.isNotEmpty) {
                            val (bytes, size) = packet.readBytes().let { byteArray ->
                                Buffer().write(byteArray) to byteArray.size.toLong()
                            }
                            sink.write(bytes, size).also {
                                kaliumLogger.d("writing $size to temp file")
                                kaliumLogger.d("bytes buffer size ${bytes.size}")
                                bytes.clear()
                                sink.flush()
                            }
                        }
                    }
                    channel.cancel()
                    sink.close().also {
                        kaliumLogger.d("closing")
                    }
                }
                NetworkResponse.Success(Unit, emptyMap(), 200)
            }
        } catch (exception: Exception) {
            NetworkResponse.Error(KaliumException.GenericError(exception))
        }
    }

    /**
     * Build path for assets endpoint download.
     * The case for using V3 is a fallback and should not happen.
     *
     * TODO(assets): once API v2 is alive, this should be changed/merged.
     * https://github.com/wireapp/wire-server/blob/dfe207073b54a63372898a75f670e972dd482118/changelog.d/1-api-changes/api-versioning
     */
    private fun buildAssetsPath(assetId: AssetId): String {
        return if (assetId.domain.isNotBlank()) "$PATH_PUBLIC_ASSETS_V4/${assetId.domain}/${assetId.value}"
        else "$PATH_PUBLIC_ASSETS_V3/${assetId.value}"
    }

    override suspend fun uploadAsset(
        metadata: AssetMetadataRequest,
        encryptedDataSource: Source,
        encryptedDataSize: Long
    ): NetworkResponse<AssetResponse> =
        wrapKaliumResponse {
            httpClient.post(PATH_PUBLIC_ASSETS_V3) {
                contentType(ContentType.MultiPart.Mixed)
                setBody(StreamAssetContent(metadata, encryptedDataSize, encryptedDataSource))
            }
        }

    override suspend fun deleteAsset(assetId: AssetId, assetToken: String?): NetworkResponse<Unit> =
        wrapKaliumResponse {
            httpClient.delete(buildAssetsPath(assetId)) {
                assetToken?.let { header(HEADER_ASSET_TOKEN, it) }
            }
        }

    private companion object {
        const val PATH_PUBLIC_ASSETS_V3 = "assets/v3"
        const val PATH_PUBLIC_ASSETS_V4 = "assets/v4"
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
        channel.close()
    }
}
