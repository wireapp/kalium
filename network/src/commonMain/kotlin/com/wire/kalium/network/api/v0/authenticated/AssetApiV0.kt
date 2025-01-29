/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.network.api.v0.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.base.authenticated.asset.AssetApi
import com.wire.kalium.network.api.authenticated.asset.AssetMetadataRequest
import com.wire.kalium.network.api.authenticated.asset.AssetResponse
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
import io.ktor.client.statement.HttpResponse
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
import okio.Buffer
import okio.Sink
import okio.Source
import okio.use
import kotlin.coroutines.cancellation.CancellationException

internal open class AssetApiV0 internal constructor(
    private val authenticatedNetworkClient: AuthenticatedNetworkClient
) : AssetApi {

    private val httpClient get() = authenticatedNetworkClient.httpClient

    override suspend fun downloadAsset(
        assetId: String,
        assetDomain: String?,
        assetToken: String?,
        tempFileSink: Sink
    ): NetworkResponse<Unit> = runCatching {
        httpClient.prepareGet(buildAssetsPath(assetId, assetDomain)) {
            if (!assetToken.isNullOrBlank()) {
                header(HEADER_ASSET_TOKEN, assetToken)
            }
        }.execute { httpResponse ->
            if (httpResponse.status.isSuccess()) {
                handleAssetContentDownload(httpResponse, tempFileSink)
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
    }.getOrElse { unhandledException ->
        if (unhandledException is CancellationException) {
            throw unhandledException
        }
        // since we are handling manually our network exceptions for this endpoint, handle ie: no host exception
        NetworkResponse.Error(KaliumException.GenericError(unhandledException))
    }

    @Suppress("TooGenericExceptionCaught", "NestedBlockDepth")
    private suspend fun handleAssetContentDownload(httpResponse: HttpResponse, tempFileSink: Sink) = try {
        val channel = httpResponse.body<ByteReadChannel>()
        tempFileSink.use { sink ->
            while (!channel.isClosedForRead) {
                val packet = channel.readRemaining(BUFFER_SIZE)
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
        if (e is CancellationException) {
            throw e
        }
        NetworkResponse.Error(KaliumException.GenericError(e))
    }

    /**
     * Build path for assets endpoint download.
     * The case for using V3 is a fallback and should not happen.
     */
    protected open fun buildAssetsPath(assetId: String, assetDomain: String?): String = if (assetDomain.isNullOrBlank()) {
        "$PATH_PUBLIC_ASSETS_V3/$assetId"
    } else {
        "$PATH_PUBLIC_ASSETS_V4/$assetDomain/$assetId"
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

    override suspend fun deleteAsset(assetId: String, assetDomain: String?, assetToken: String?): NetworkResponse<Unit> =
        wrapKaliumResponse {
            httpClient.delete(buildAssetsPath(assetId, assetDomain)) {
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
