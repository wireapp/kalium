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
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import okio.Buffer
import okio.BufferedSource
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.SYSTEM
import okio.buffer
import okio.use

internal class CellsS3Client(
    private val httpClient: HttpClient,
    private val endpointProvider: suspend () -> String,
    private val credentialsProvider: suspend () -> S3Credentials,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val signer: AwsSigV4Signer = AwsSigV4Signer(DEFAULT_REGION, S3_SERVICE_NAME),
    private val dateProvider: () -> AwsSigningDate = { AwsSigningDate.now() },
) : CellsAwsClient {

    override suspend fun download(
        objectKey: String,
        outFileSink: okio.Sink,
        onProgressUpdate: (Long) -> Unit,
    ) {
        val signedRequest = signedRequest(
            method = HttpMethod.Get,
            objectKey = objectKey,
        )
        val response = httpClient.request(signedRequest.url) {
            method = HttpMethod.Get
            signedRequest.headers.forEach { (name, value) -> header(name, value) }
        }
        response.ensureSuccess("Download object")
        response.bodyAsChannel().copyToSink(outFileSink, onProgressUpdate = onProgressUpdate)
    }

    override suspend fun upload(path: Path, node: CellNodeDTO, onProgressUpdate: (Long) -> Unit) {
        val length = fileSystem.metadata(path).size
            ?: throw IOException("Upload source size is not available")

        if (length > MAX_REGULAR_UPLOAD_SIZE) {
            uploadMultipart(path, length, node, onProgressUpdate)
        } else {
            uploadRegular(path, length, node, onProgressUpdate)
        }
    }

    override suspend fun getPreSignedUrl(objectKey: String): String {
        val endpoint = endpointProvider()
        val credentials = credentialsProvider()
        val date = dateProvider()
        val s3Url = S3UrlBuilder.build(endpoint, DEFAULT_BUCKET_NAME, objectKey)
        return signer.presignGetUrl(
            url = s3Url,
            credentials = credentials,
            signingDate = date,
            expiresSeconds = PRESIGNED_GET_EXPIRATION_SECONDS,
        )
    }

    private suspend fun uploadRegular(
        path: Path,
        length: Long,
        node: CellNodeDTO,
        onProgressUpdate: (Long) -> Unit,
    ) {
        val signedRequest = signedRequest(
            method = HttpMethod.Put,
            objectKey = node.path,
            signedHeaders = node.createDraftNodeHeaders(),
        )
        val response = httpClient.request(signedRequest.url) {
            method = HttpMethod.Put
            signedRequest.headers.forEach { (name, value) -> header(name, value) }
            setBody(S3FileContent(fileSystem, path, length, onProgressUpdate))
        }
        response.ensureSuccess("Upload object")
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
        val signedRequest = signedRequest(
            method = HttpMethod.Post,
            objectKey = node.path,
            queryParameters = listOf(S3QueryParameter(UPLOADS_QUERY_PARAMETER, "")),
            signedHeaders = node.createDraftNodeHeaders(),
        )
        val response = httpClient.request(signedRequest.url) {
            method = HttpMethod.Post
            signedRequest.headers.forEach { (name, value) -> header(name, value) }
        }
        response.ensureSuccess("Create multipart upload")
        return response.bodyAsText().xmlTagValue("UploadId")
            ?: throw IOException("Create multipart upload response did not include an UploadId")
    }

    private suspend fun uploadPart(
        objectKey: String,
        uploadId: String,
        partNumber: Int,
        partData: ByteArray,
    ): String {
        val signedRequest = signedRequest(
            method = HttpMethod.Put,
            objectKey = objectKey,
            queryParameters = listOf(
                S3QueryParameter("partNumber", partNumber.toString()),
                S3QueryParameter("uploadId", uploadId),
            ),
        )
        val response = httpClient.request(signedRequest.url) {
            method = HttpMethod.Put
            signedRequest.headers.forEach { (name, value) -> header(name, value) }
            setBody(ByteArrayContent(partData, ContentType.Application.OctetStream))
        }
        response.ensureSuccess("Upload multipart part")
        return response.headers[HttpHeaders.ETag]
            ?: throw IOException("Upload multipart part response did not include an ETag")
    }

    private suspend fun completeMultipartUpload(
        objectKey: String,
        uploadId: String,
        completedParts: List<CompletedS3Part>,
    ) {
        val body = completedParts.toCompleteMultipartUploadXml()
        val signedRequest = signedRequest(
            method = HttpMethod.Post,
            objectKey = objectKey,
            queryParameters = listOf(S3QueryParameter("uploadId", uploadId)),
        )
        val response = httpClient.request(signedRequest.url) {
            method = HttpMethod.Post
            signedRequest.headers.forEach { (name, value) -> header(name, value) }
            setBody(TextContent(body, ContentType.Application.Xml))
        }
        response.ensureSuccess("Complete multipart upload")
    }

    private suspend fun signedRequest(
        method: HttpMethod,
        objectKey: String,
        queryParameters: List<S3QueryParameter> = emptyList(),
        signedHeaders: Map<String, String> = emptyMap(),
    ): SignedS3Request {
        val endpoint = endpointProvider()
        val credentials = credentialsProvider()
        val date = dateProvider()
        val s3Url = S3UrlBuilder.build(endpoint, DEFAULT_BUCKET_NAME, objectKey, queryParameters)
        val headers = linkedMapOf(
            HttpHeaders.Host to s3Url.hostHeader,
            X_AMZ_CONTENT_SHA256 to UNSIGNED_PAYLOAD,
            X_AMZ_DATE to date.dateTime,
        )
        signedHeaders.forEach { (name, value) -> headers[name] = value }

        return signer.sign(
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
            var uploaded = 0L
            fileSystem.source(path).buffer().use { source ->
                val buffer = Buffer()
                while (source.read(buffer, STREAM_BUFFER_SIZE) != -1L) {
                    val bytes = buffer.readByteArray()
                    channel.writeFully(bytes)
                    uploaded += bytes.size
                    onProgressUpdate(uploaded)
                }
            }
            channel.flush()
        }
    }

    private companion object {
        const val DEFAULT_REGION = "us-east-1"
        const val DEFAULT_BUCKET_NAME = "io"
        const val MAX_REGULAR_UPLOAD_SIZE = 100 * 1024 * 1024L
        const val MULTIPART_CHUNK_SIZE = 10 * 1024 * 1024
        const val PRESIGNED_GET_EXPIRATION_SECONDS = 24 * 60 * 60
        const val STREAM_BUFFER_SIZE = 8 * 1024L
        const val S3_SERVICE_NAME = "s3"
        const val UNSIGNED_PAYLOAD = "UNSIGNED-PAYLOAD"
        const val UPLOADS_QUERY_PARAMETER = "uploads"
        const val X_AMZ_CONTENT_SHA256 = "x-amz-content-sha256"
        const val X_AMZ_DATE = "x-amz-date"
    }
}

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

private fun HttpResponse.ensureSuccess(operation: String) {
    if (!status.isSuccess()) {
        throw IOException("$operation failed: ${status.value} ${status.description}")
    }
}
