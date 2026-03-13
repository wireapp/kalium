/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadAllMessagesResponse
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadConversationMetadataResponse
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadMessageEventsRequest
import com.wire.kalium.network.api.base.authenticated.nomaddevice.NomadDeviceSyncApi
import com.wire.kalium.network.exceptions.APINotSupported
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.handleUnsuccessfulResponse
import com.wire.kalium.network.utils.setUrl
import com.wire.kalium.network.utils.wrapRequest
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.charsets.Charsets
import io.ktor.utils.io.close
import io.ktor.utils.io.core.toByteArray
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writeStringUtf8
import okio.Buffer
import okio.Sink
import okio.Source
import okio.buffer
import okio.use
import kotlin.coroutines.cancellation.CancellationException

internal open class NomadDeviceSyncApiV0 internal constructor(
    private val authenticatedNetworkClient: AuthenticatedNetworkClient,
    private val nomadServiceUrl: String? = null
) : NomadDeviceSyncApi {

    private val httpClient get() = authenticatedNetworkClient.httpClient

    override suspend fun postMessageEvents(request: NomadMessageEventsRequest): NetworkResponse<Unit> =
        requireNomadServiceUrl(apiName = "postMessageEvents") ?: wrapRequest {
            httpClient.post {
                setNomadUrlIfAvailable(PATH_EVENT, PATH_MESSAGES)
                setBody(request)
                contentType(ContentType.Application.Json)
            }
        }

    override suspend fun getAllMessages(): NetworkResponse<NomadAllMessagesResponse> =
        requireNomadServiceUrl(apiName = "getAllMessages") ?: wrapRequest {
            httpClient.get {
                setNomadUrlIfAvailable(PATH_EVENT, PATH_MESSAGES)
            }
        }

    override suspend fun getConversationMetadata(): NetworkResponse<NomadConversationMetadataResponse> =
        requireNomadServiceUrl(apiName = "getConversationMetadata") ?: wrapRequest {
            httpClient.get {
                setNomadUrlIfAvailable(PATH_EVENT, PATH_CONVERSATION_METADATA)
            }
        }

    override suspend fun uploadCryptoState(
        clientId: String,
        backupSource: () -> Source,
        backupSize: Long
    ): NetworkResponse<Unit> =
        requireNomadServiceUrl(apiName = "uploadCryptoState") ?: wrapRequest {
            httpClient.post {
                setNomadUrlIfAvailable(PATH_EVENT, PATH_CRYPTO_STATE)
                parameter(QUERY_DEVICE_ID, clientId)
                setBody(StreamCryptoStateBodyContent(backupSource, backupSize))
            }
        }

    override suspend fun downloadCryptoState(tempBackupFileSink: Sink): NetworkResponse<Unit> =
        requireNomadServiceUrl(apiName = "downloadCryptoState") ?: runCatching {
            httpClient.prepareGet {
                setNomadUrlIfAvailable(PATH_EVENT, PATH_CRYPTO_STATE)
            }.execute { httpResponse ->
                if (httpResponse.status.isSuccess()) {
                    handleCryptoStateDownload(httpResponse, tempBackupFileSink)
                } else {
                    handleUnsuccessfulResponse(httpResponse)
                }
            }
        }.getOrElse { unhandledException ->
            if (unhandledException is CancellationException) {
                throw unhandledException
            }
            NetworkResponse.Error(KaliumException.GenericError(unhandledException))
        }

    @Suppress("TooGenericExceptionCaught", "NestedBlockDepth")
    private suspend fun handleCryptoStateDownload(
        httpResponse: HttpResponse,
        tempFileSink: Sink
    ): NetworkResponse<Unit> = try {
        val channel = httpResponse.body<io.ktor.utils.io.ByteReadChannel>()
        tempFileSink.buffer().use { bufferedSink ->
            val array = ByteArray(BUFFER_SIZE.toInt())
            while (!channel.isClosedForRead) {
                val read = channel.readAvailable(array, 0, array.size)
                if (read <= 0) break
                bufferedSink.write(array, 0, read)
            }
        }
        NetworkResponse.Success(Unit, httpResponse)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        NetworkResponse.Error(KaliumException.GenericError(e))
    }

    /**
     * Custom [OutgoingContent] implementation to stream the crypto state backup as multipart/form-data without loading it all into memory.
     * The content is structured as follows:
     * --boundary
     * Content-Disposition: form-data; name="file"; filename="CHANGELOG.zip"
     * Content-Type: application/octet-stream
     * Content-Length: <fileSize>
     * <file content streamed from backupSource>
     * --boundary--
     */
    internal class StreamCryptoStateBodyContent(
        private val fileContentStream: () -> Source,
        private val fileSize: Long
    ) : OutgoingContent.WriteChannelContent() {

        override val contentLength: Long
            get() = openingData.toByteArray(Charsets.UTF_8).size +
                    fileSize +
                    closingData.toByteArray(Charsets.UTF_8).size

        override val contentType: ContentType =
            ContentType.MultiPart.FormData.withParameter("boundary", BOUNDARY)
        private val openingData: String by lazy {
            buildString {
                append("--$BOUNDARY\r\n")
                append("Content-Disposition: form-data; name=\"file\"; filename=\"$CRYPTO_ZIP_FILENAME\"\r\n")
                append("Content-Type: application/octet-stream\r\n")
                append("Content-Length: $fileSize\r\n\r\n")
            }
        }
        private val closingData = "\r\n--$BOUNDARY--\r\n"
        override suspend fun writeTo(channel: ByteWriteChannel) {
            channel.writeStringUtf8(openingData)
            fileContentStream().buffer().use { source ->
                val buffer = Buffer()
                while (source.read(buffer, BUFFER_SIZE) != -1L) {
                    val byteArray = buffer.readByteArray()
                    channel.writeFully(byteArray)
                }
            }
            channel.writeStringUtf8(closingData)
            channel.flush()
            channel.close()
        }
    }

    private fun HttpRequestBuilder.setNomadUrlIfAvailable(vararg path: String) {
        nomadServiceUrl?.let {
            val normalizedPath = path.flatMap { value -> value.split(PATH_SEPARATOR).filter(String::isNotEmpty) }
            setUrl(it, normalizedPath)
        }
    }

    private fun <T : Any> requireNomadServiceUrl(apiName: String): NetworkResponse<T>? =
        if (nomadServiceUrl == null) {
            NetworkResponse.Error(
                APINotSupported(
                    "$API_NAME.$apiName requires a configured Nomad service URL. " +
                            "Request was short-circuited and no API call was made."
                )
            )
        } else {
            null
        }

    private companion object {
        const val PATH_EVENT = "event"
        const val PATH_MESSAGES = "messages"
        const val PATH_CONVERSATION_METADATA = "conversation/metadata"
        const val PATH_CRYPTO_STATE = "crypto/state"
        const val QUERY_DEVICE_ID = "device_id"
        const val BUFFER_SIZE = 8L * 1024
        const val BOUNDARY = "frontier"
        const val CRYPTO_ZIP_FILENAME = "CHANGELOG.zip"
        const val PATH_SEPARATOR = "/"
        const val API_NAME = "NomadDeviceSyncApiV0"
    }
}
