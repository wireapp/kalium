package com.wire.kalium.network.api.v0.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.base.authenticated.asset.AssetApi
import com.wire.kalium.network.api.base.authenticated.asset.AssetMetadataRequest
import com.wire.kalium.network.api.base.authenticated.asset.AssetResponse
import com.wire.kalium.network.api.base.model.AssetId
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
import io.ktor.utils.io.close
import io.ktor.utils.io.core.ByteReadPacket
import io.ktor.utils.io.core.isNotEmpty
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.writeStringUtf8
import okio.Buffer
import okio.Sink
import okio.Source
import okio.use

internal open class AssetApiV0 internal constructor(
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
        encryptedDataSource: () -> Source,
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

internal class StreamAssetContent internal constructor(
    private val metadata: AssetMetadataRequest,
    private val encryptedDataSize: Long,
    private val fileContentStream: () -> Source,
) : OutgoingContent.WriteChannelContent() {
    private val openingData: String by lazy {
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

        body.toString()
    }

    private val closingArray = "\r\n--frontier--\r\n"

    override suspend fun writeTo(channel: ByteWriteChannel) {
        channel.writeStringUtf8(openingData)
        val contentBuffer = Buffer()
        val fileContentStream = fileContentStream()
        while (fileContentStream.read(contentBuffer, BUFFER_SIZE) != -1L) {
            contentBuffer.readByteArray().let { content ->
                channel.writePacket(ByteReadPacket(content))
            }
        }
        channel.writeStringUtf8(closingArray)
        channel.flush()
        channel.close()
    }
}

private const val BUFFER_SIZE = 1024 * 8L
