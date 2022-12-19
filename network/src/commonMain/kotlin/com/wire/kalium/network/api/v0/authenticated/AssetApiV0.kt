package com.wire.kalium.network.api.v0.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.base.authenticated.asset.AssetApi
import com.wire.kalium.network.api.base.authenticated.asset.AssetMetadataRequest
import com.wire.kalium.network.api.base.authenticated.asset.AssetResponse
import com.wire.kalium.network.api.base.model.AssetId
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.kaliumLogger
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.handleUnsuccessfulResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.cancel
import io.ktor.utils.io.close
import io.ktor.utils.io.core.ByteReadPacket
import io.ktor.utils.io.core.isNotEmpty
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import okio.Buffer
import okio.IOException
import okio.Sink
import okio.Source
import okio.use
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

internal open class AssetApiV0 internal constructor(
    private val authenticatedNetworkClient: AuthenticatedNetworkClient
) : AssetApi {

    private val httpClient get() = authenticatedNetworkClient.httpClient

    @Suppress("TooGenericExceptionCaught")
    override suspend fun downloadAsset(assetId: AssetId, assetToken: String?, tempFileSink: Sink): NetworkResponse<Unit> =
        httpClient.prepareGet(buildAssetsPath(assetId)) {
            assetToken?.let { header(HEADER_ASSET_TOKEN, it) }
        }.execute { httpResponse ->
            if (httpResponse.status.isSuccess()) {
                try {
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
                    NetworkResponse.Success(Unit, httpResponse)
                } catch (e: Exception) {
                    NetworkResponse.Error(KaliumException.GenericError(e))
                }
            } else {
                handleUnsuccessfulResponse(httpResponse).also {
                    if (it.kException is KaliumException.InvalidRequestError &&
                        it.kException.errorResponse.code == HttpStatusCode.Unauthorized.value
                    ) {
                        kaliumLogger.d("""ASSETS 401: "WWWAuthenticate header": "${httpResponse.headers[HttpHeaders.WWWAuthenticate]}"""")
                    }
                }
            }
        }

    /**
     * Build path for assets endpoint download.
     * The case for using V3 is a fallback and should not happen.
     */
    protected open fun buildAssetsPath(assetId: AssetId): String {
        return if (assetId.domain.isNotBlank()) "$PATH_PUBLIC_ASSETS_V4/${assetId.domain}/${assetId.value}"
        else "$PATH_PUBLIC_ASSETS_V3/${assetId.value}"
    }

    override suspend fun uploadAsset(
        metadata: AssetMetadataRequest,
        encryptedDataSource: () -> Source,
        encryptedDataSize: Long
    ): NetworkResponse<AssetResponse> {
        return wrapKaliumResponse {
            httpClient.post(PATH_PUBLIC_ASSETS_V3) {
                contentType(ContentType.MultiPart.Mixed)
                setBody(StreamAssetContent(metadata, encryptedDataSize, encryptedDataSource, coroutineContext))
            }
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
    callContext: CoroutineContext,
) : OutgoingContent.WriteChannelContent(), CoroutineScope {

    private val producerJob = Job(callContext[Job])

    override val coroutineContext: CoroutineContext = callContext + producerJob

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

    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun writeTo(channel: ByteWriteChannel) {
        try {
            supervisorScope {
                if (!channel.isClosedForWrite && producerJob.isActive) {

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
        } catch (e: Exception) {
            channel.flush()
            channel.close()
            producerJob.completeExceptionally(e)

            throw IOException(e.message)
        } finally {
            producerJob.complete()
        }
    }
}

private const val BUFFER_SIZE = 1024 * 8L
