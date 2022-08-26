package com.wire.kalium.network.api.asset

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.AssetId
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.cancel
import io.ktor.utils.io.charsets.Charsets.UTF_8
import io.ktor.utils.io.core.isNotEmpty
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.core.toByteArray
import okio.Buffer
import okio.Sink
import okio.Source
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

    @Suppress("TooGenericExceptionCaught")
    override suspend fun downloadAsset(assetId: AssetId, assetToken: String?, tempFileSink: Sink): NetworkResponse<Unit> {
        return try {
            httpClient.prepareGet(buildAssetsPath(assetId)) {
                assetToken?.let { header(HEADER_ASSET_TOKEN, it) }
            }.execute { httpResponse ->
                val channel = httpResponse.body<ByteReadChannel>()
                tempFileSink.use { sink ->
                    while (!channel.isClosedForRead) {
                        val packet = channel.readRemaining(BUFFER_SIZE, 0)
                        while (packet.isNotEmpty) {
                            val (bytes, size) = packet.readBytes().let { byteArray ->
                                Buffer().write(byteArray) to byteArray.size.toLong()
                            }
                            sink.write(bytes, size).also {
                                bytes.clear()
                                sink.flush()
                            }
                        }
                    }
                    channel.cancel()
                    sink.close()
                }
                NetworkResponse.Success(Unit, emptyMap(), HttpStatusCode.OK.value)
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
        // first write, opening data, offset = 0
        channel.writeFully(openingData, 0, openingData.size)
        // todo: implement the correct offset handling for this method
        // we are getting double reads ?
        // can we just use one read channel or just see if a channel can be transformed into another thing
        // the channel is the output / baos / dst file etc.
        val contentBuffer = Buffer()
        var byteCount: Long
        var index = openingData.size
        // second + n writes, offset = dynamic, starting by openingData.size
        while (fileContentStream.read(contentBuffer, BUFFER_SIZE).also { byteCount = it } != -1L) {
            channel.writeFully(contentBuffer.readByteArray(), index, byteCount.toInt())
            index += byteCount.toInt()
        }
        val finalOffset = index - byteCount
        channel.writeFully(closingArray, finalOffset.toInt(), closingArray.size)
        channel.close(null)
    }
}

private const val BUFFER_SIZE = 1024 * 8L
