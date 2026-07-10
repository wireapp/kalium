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
package com.wire.kalium.cells.data

import com.wire.kalium.cells.data.model.CellNodeDTO
import io.ktor.client.HttpClient
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteWriteChannel
import kotlinx.coroutines.delay
import kotlinx.io.IOException as KtorIOException
import okio.Buffer
import okio.BufferedSource
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.SYSTEM
import okio.buffer
import okio.use
import kotlin.random.Random

internal class CellsS3Client(
    private val httpClient: HttpClient,
    private val endpointProvider: suspend () -> String,
    private val credentialsProvider: suspend () -> S3Credentials,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val config: CellsS3ClientConfig = CellsS3ClientConfig(),
) : CellsAwsClient {

    override suspend fun download(
        objectKey: String,
        outFileSink: okio.Sink,
        onProgressUpdate: (Long) -> Unit,
    ) {
        val response = requestWithRetry(
            operation = "Download object",
            request = {
                val signedRequest = signedRequest(
                    method = HttpMethod.Get,
                    objectKey = objectKey,
                )
                httpClient.request(signedRequest.url) {
                    method = HttpMethod.Get
                    signedRequest.headers.forEach { (name, value) -> header(name, value) }
                }
            },
            transform = { it },
        )
        response.bodyAsChannel().copyToSink(outFileSink, onProgressUpdate = onProgressUpdate)
    }

    override suspend fun upload(path: Path, node: CellNodeDTO, onProgressUpdate: (Long) -> Unit) {
        val length = fileSystem.metadata(path).size
            ?: throw IOException("Upload source size is not available")

        if (length > config.maxRegularUploadSize) {
            uploadMultipart(path, length, node, onProgressUpdate)
        } else {
            uploadRegular(path, length, node, onProgressUpdate)
        }
    }

    private suspend fun uploadRegular(
        path: Path,
        length: Long,
        node: CellNodeDTO,
        onProgressUpdate: (Long) -> Unit,
    ) {
        val progressReporter = MonotonicProgressReporter(onProgressUpdate)
        requestWithRetry(
            operation = "Upload object",
            request = {
                val signedRequest = signedRequest(
                    method = HttpMethod.Put,
                    objectKey = node.path,
                    signedHeaders = node.createDraftNodeHeaders(),
                )
                httpClient.request(signedRequest.url) {
                    method = HttpMethod.Put
                    signedRequest.headers.forEach { (name, value) -> header(name, value) }
                    setBody(S3FileContent(fileSystem, path, length, progressReporter::report))
                }
            },
            transform = { response ->
                response.discardBody()
                Unit
            },
        )
    }

    private suspend fun uploadMultipart(
        path: Path,
        length: Long,
        node: CellNodeDTO,
        onProgressUpdate: (Long) -> Unit,
    ) {
        val uploadId = createMultipartUpload(node)
        val completedParts = mutableListOf<CompletedS3Part>()
        var uploaded = 0L
        var partNumber = 1

        fileSystem.source(path).buffer().use { source ->
            while (uploaded < length) {
                val partSize = minOf(MULTIPART_CHUNK_SIZE.toLong(), length - uploaded)
                val partData = source.readPart(partSize)
                val eTag = uploadPart(node.path, uploadId, partNumber, partData)
                uploaded += partData.size
                onProgressUpdate(uploaded)
                completedParts += CompletedS3Part(partNumber, eTag)
                partNumber++
            }
        }

        completeMultipartUpload(node.path, uploadId, completedParts)
    }

    private suspend fun createMultipartUpload(node: CellNodeDTO): String {
        val responseBody = requestWithRetry(
            operation = "Create multipart upload",
            request = {
                val signedRequest = signedRequest(
                    method = HttpMethod.Post,
                    objectKey = node.path,
                    queryParameters = listOf(S3QueryParameter(UPLOADS_QUERY_PARAMETER, "")),
                    signedHeaders = node.createDraftNodeHeaders(),
                )
                httpClient.request(signedRequest.url) {
                    method = HttpMethod.Post
                    signedRequest.headers.forEach { (name, value) -> header(name, value) }
                }
            },
            transform = { it.bodyAsText() },
        )
        return responseBody.xmlTagValue("UploadId")
            ?: throw IOException("Create multipart upload response did not include an UploadId")
    }

    private suspend fun uploadPart(
        objectKey: String,
        uploadId: String,
        partNumber: Int,
        partData: ByteArray,
    ): String {
        val eTag = requestWithRetry(
            operation = "Upload multipart part",
            request = {
                val signedRequest = signedRequest(
                    method = HttpMethod.Put,
                    objectKey = objectKey,
                    queryParameters = listOf(
                        S3QueryParameter("partNumber", partNumber.toString()),
                        S3QueryParameter("uploadId", uploadId),
                    ),
                )
                httpClient.request(signedRequest.url) {
                    method = HttpMethod.Put
                    signedRequest.headers.forEach { (name, value) -> header(name, value) }
                    setBody(ByteArrayContent(partData, ContentType.Application.OctetStream))
                }
            },
            transform = { response ->
                val responseETag = response.headers[HttpHeaders.ETag]
                response.discardBody()
                responseETag
            },
        )
        return eTag
            ?: throw IOException("Upload multipart part response did not include an ETag")
    }

    private suspend fun completeMultipartUpload(
        objectKey: String,
        uploadId: String,
        completedParts: List<CompletedS3Part>,
    ) {
        val body = completedParts.toCompleteMultipartUploadXml()
        requestWithRetry(
            operation = "Complete multipart upload",
            request = {
                val signedRequest = signedRequest(
                    method = HttpMethod.Post,
                    objectKey = objectKey,
                    queryParameters = listOf(S3QueryParameter("uploadId", uploadId)),
                )
                httpClient.request(signedRequest.url) {
                    method = HttpMethod.Post
                    signedRequest.headers.forEach { (name, value) -> header(name, value) }
                    setBody(TextContent(body, ContentType.Application.Xml))
                }
            },
            transform = { response -> validateCompleteMultipartUploadResponse(response.bodyAsText()) },
        )
    }

    private suspend fun <T> requestWithRetry(
        operation: String,
        request: suspend () -> HttpResponse,
        transform: suspend (HttpResponse) -> T,
    ): T {
        var lastRetryableFailure: Exception? = null
        repeat(S3_MAX_ATTEMPTS) { attemptIndex ->
            when (val attempt = performRequestAttempt(operation, request, transform)) {
                is S3Attempt.Success -> return attempt.value
                is S3Attempt.TerminalFailure -> throw attempt.cause
                is S3Attempt.RetryableFailure -> {
                    lastRetryableFailure = attempt.cause
                    if (attemptIndex < S3_MAX_ATTEMPTS - 1) {
                        delay(s3RetryDelayMillis(attemptIndex + 1))
                    }
                }
            }
        }
        throw requireNotNull(lastRetryableFailure)
    }

    private suspend fun <T> performRequestAttempt(
        operation: String,
        request: suspend () -> HttpResponse,
        transform: suspend (HttpResponse) -> T,
    ): S3Attempt<T> = try {
        val response = request()
        if (!response.status.isSuccess()) {
            response.toS3Failure(operation)
        } else {
            S3Attempt.Success(transform(response))
        }
    } catch (cause: RetryableS3Exception) {
        S3Attempt.RetryableFailure(cause)
    } catch (cause: S3RequestException) {
        S3Attempt.TerminalFailure(cause)
    } catch (cause: RetryableTransportException) {
        S3Attempt.RetryableFailure(cause)
    } catch (cause: HttpRequestTimeoutException) {
        S3Attempt.RetryableFailure(cause)
    } catch (cause: ConnectTimeoutException) {
        S3Attempt.RetryableFailure(cause)
    } catch (cause: SocketTimeoutException) {
        S3Attempt.RetryableFailure(cause)
    } catch (cause: KtorIOException) {
        S3Attempt.RetryableFailure(cause)
    }

    private suspend fun signedRequest(
        method: HttpMethod,
        objectKey: String,
        queryParameters: List<S3QueryParameter> = emptyList(),
        signedHeaders: Map<String, String> = emptyMap(),
    ): SignedS3Request {
        val endpoint = endpointProvider()
        val credentials = credentialsProvider()
        val date = config.dateProvider()
        val s3Url = S3UrlBuilder.build(endpoint, DEFAULT_BUCKET_NAME, objectKey, queryParameters)
        val headers = linkedMapOf(
            HttpHeaders.Host to s3Url.hostHeader,
            X_AMZ_CONTENT_SHA256 to UNSIGNED_PAYLOAD,
            X_AMZ_DATE to date.dateTime,
        )
        signedHeaders.forEach { (name, value) -> headers[name] = value }

        return config.signer.sign(
            method = method.value,
            url = s3Url,
            headers = headers,
            payloadHash = UNSIGNED_PAYLOAD,
            credentials = credentials,
            signingDate = date,
        )
    }

    private class S3FileContent(
        private val fileSystem: FileSystem,
        private val path: Path,
        override val contentLength: Long,
        private val onProgressUpdate: (Long) -> Unit,
    ) : OutgoingContent.WriteChannelContent() {
        override val contentType: ContentType = ContentType.Application.OctetStream

        override suspend fun writeTo(channel: ByteWriteChannel) {
            try {
                transferFile(channel)
            } catch (cause: RetryableTransportException) {
                throw cause
            } catch (cause: UploadSourceException) {
                throw cause
            } catch (cause: IOException) {
                throw UploadSourceException("Failed to read upload source")
            }
            channel.flushUpload()
        }

        private suspend fun transferFile(channel: ByteWriteChannel) {
            var uploaded = 0L
            fileSystem.source(path).buffer().use { source ->
                val buffer = Buffer()
                var read = source.read(buffer, STREAM_BUFFER_SIZE)
                while (read != -1L) {
                    val bytes = buffer.readByteArray()
                    channel.writeUploadBytes(bytes)
                    uploaded += bytes.size
                    onProgressUpdate(uploaded)
                    read = source.read(buffer, STREAM_BUFFER_SIZE)
                }
            }
        }
    }

    private class MonotonicProgressReporter(
        private val onProgressUpdate: (Long) -> Unit,
    ) {
        private var lastReported = 0L

        fun report(uploaded: Long) {
            if (uploaded > lastReported) {
                lastReported = uploaded
                onProgressUpdate(uploaded)
            }
        }
    }

    private companion object {
        const val DEFAULT_BUCKET_NAME = "io"
        const val MULTIPART_CHUNK_SIZE = 10 * 1024 * 1024
        const val STREAM_BUFFER_SIZE = 8 * 1024L
        const val UNSIGNED_PAYLOAD = "UNSIGNED-PAYLOAD"
        const val UPLOADS_QUERY_PARAMETER = "uploads"
        const val X_AMZ_CONTENT_SHA256 = "x-amz-content-sha256"
        const val X_AMZ_DATE = "x-amz-date"
    }
}

internal data class CellsS3ClientConfig(
    val signer: AwsSigV4Signer = AwsSigV4Signer(DEFAULT_S3_REGION, S3_SERVICE_NAME),
    val dateProvider: () -> AwsSigningDate = { AwsSigningDate.now() },
    val maxRegularUploadSize: Long = DEFAULT_MAX_REGULAR_UPLOAD_SIZE,
)

internal data class S3Credentials(
    val accessKeyId: String,
    val secretAccessKey: String,
)

internal data class CompletedS3Part(
    val partNumber: Int,
    val eTag: String,
)

internal fun CellNodeDTO.createDraftNodeHeaders(): Map<String, String> = mapOf(
    "x-amz-meta-draft-mode" to "true",
    "x-amz-meta-create-resource-uuid" to uuid,
    "x-amz-meta-create-version-id" to versionId,
)

private fun BufferedSource.readPart(size: Long): ByteArray {
    val buffer = Buffer()
    var remaining = size
    while (remaining > 0L) {
        val read = read(buffer, remaining)
        if (read == -1L) {
            throw IOException("Unexpected end of upload source")
        }
        remaining -= read
    }
    return buffer.readByteArray()
}

private fun List<CompletedS3Part>.toCompleteMultipartUploadXml(): String = buildString {
    append("<CompleteMultipartUpload>")
    this@toCompleteMultipartUploadXml.forEach { part ->
        append("<Part>")
        append("<PartNumber>")
        append(part.partNumber)
        append("</PartNumber>")
        append("<ETag>")
        append(part.eTag.escapeXml())
        append("</ETag>")
        append("</Part>")
    }
    append("</CompleteMultipartUpload>")
}

private fun String.escapeXml(): String =
    replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

private fun String.xmlTagValue(tagName: String): String? {
    val startTag = "<$tagName>"
    val endTag = "</$tagName>"
    val start = indexOf(startTag).takeIf { it >= 0 }?.plus(startTag.length)
    val end = start?.let { indexOf(endTag, it).takeIf { end -> end >= it } }
    return if (start != null && end != null) substring(start, end) else null
}

private fun String.embeddedS3Error(): S3Error? {
    if (!S3_ERROR_ELEMENT.containsMatchIn(this)) return null
    return S3Error(
        code = xmlTagValue("Code"),
    )
}

private fun String.containsCompleteMultipartUploadResult(): Boolean =
    S3_COMPLETE_MULTIPART_RESULT_ELEMENT.containsMatchIn(this)

private fun validateCompleteMultipartUploadResponse(responseBody: String) {
    val embeddedError = responseBody.embeddedS3Error()
    if (embeddedError == null && responseBody.containsCompleteMultipartUploadResult()) return
    if (embeddedError == null) {
        throw RetryableS3Exception("Complete multipart upload returned an invalid response")
    }
    val message = "Complete multipart upload failed: ${embeddedError.code ?: "unknown S3 error"}"
    throw if (embeddedError.isRetryable()) RetryableS3Exception(message) else S3RequestException(message)
}

private fun S3Error.isRetryable(): Boolean = code in RETRYABLE_S3_ERROR_CODES

private fun s3RetryDelayMillis(retryCount: Int): Long {
    val maximumDelay = S3_RETRY_MAX_DELAYS[retryCount - 1]
    return Random.nextLong(maximumDelay + 1)
}

private data class S3Error(
    val code: String?,
)

private fun HttpStatusCode.isRetryableS3Status(): Boolean = value in RETRYABLE_S3_STATUS_CODES

private suspend fun HttpResponse.discardBody() = bodyAsChannel().cancel(null)

private suspend fun HttpResponse.toS3Failure(operation: String): S3Attempt<Nothing> {
    val embeddedError = bodyAsText().embeddedS3Error()
    val errorCode = embeddedError?.code?.let { ": $it" }.orEmpty()
    val exception = S3RequestException("$operation failed: ${status.value} ${status.description}$errorCode")
    return if (status.isRetryableS3Status() || embeddedError?.isRetryable() == true) {
        S3Attempt.RetryableFailure(exception)
    } else {
        S3Attempt.TerminalFailure(exception)
    }
}

private open class S3RequestException(message: String) : IOException(message)

private class RetryableS3Exception(message: String) : S3RequestException(message)

private class UploadSourceException(message: String) : S3RequestException(message)

private sealed interface S3Attempt<out T> {
    data class Success<T>(val value: T) : S3Attempt<T>
    data class RetryableFailure(val cause: Exception) : S3Attempt<Nothing>
    data class TerminalFailure(val cause: Exception) : S3Attempt<Nothing>
}

private const val S3_MAX_ATTEMPTS = 3
private const val S3_FIRST_RETRY_MAX_DELAY_MILLIS = 10L
private const val S3_SECOND_RETRY_MAX_DELAY_MILLIS = 15L
private const val DEFAULT_S3_REGION = "us-east-1"
private const val S3_SERVICE_NAME = "s3"
private const val DEFAULT_MAX_REGULAR_UPLOAD_SIZE = 100 * 1024 * 1024L
private val RETRYABLE_S3_STATUS_CODES = setOf(
    HttpStatusCode.RequestTimeout.value,
    HttpStatusCode.TooManyRequests.value,
    HttpStatusCode.InternalServerError.value,
    HttpStatusCode.BadGateway.value,
    HttpStatusCode.ServiceUnavailable.value,
    HttpStatusCode.GatewayTimeout.value,
)
private val S3_ERROR_ELEMENT = Regex("""<(?:(?:[A-Za-z_][\w.-]*):)?Error(?:\s[^>]*)?>""")
private val S3_COMPLETE_MULTIPART_RESULT_ELEMENT =
    Regex("""<(?:(?:[A-Za-z_][\w.-]*):)?CompleteMultipartUploadResult(?:\s[^>]*)?/?>""")
private val S3_RETRY_MAX_DELAYS = longArrayOf(
    S3_FIRST_RETRY_MAX_DELAY_MILLIS,
    S3_SECOND_RETRY_MAX_DELAY_MILLIS,
)
private val RETRYABLE_S3_ERROR_CODES = setOf(
    "BandwidthLimitExceeded",
    "InternalError",
    "PriorRequestNotComplete",
    "RequestThrottled",
    "RequestThrottledException",
    "RequestTimeout",
    "RequestTimeoutException",
    "ServiceUnavailable",
    "SlowDown",
    "Throttling",
    "ThrottlingException",
)
