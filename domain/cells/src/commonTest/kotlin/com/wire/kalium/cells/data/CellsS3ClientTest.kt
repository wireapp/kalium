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
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import okio.ForwardingFileSystem
import okio.Path.Companion.toPath
import okio.Source
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CellsS3ClientTest {

    @Test
    fun givenSmallFile_whenUploading_thenPutObjectRequestIsSignedWithDraftMetadata() = runTest {
        var capturedRequest: HttpRequestData? = null
        val fileSystem = FakeFileSystem()
        val uploadPath = "/upload.txt".toPath()
        fileSystem.write(uploadPath) {
            write("hello cells".encodeToByteArray())
        }
        val httpClient = HttpClient(
            MockEngine { request ->
                capturedRequest = request
                respond(content = "", status = HttpStatusCode.OK)
            }
        )
        val client = CellsS3Client(
            httpClient = httpClient,
            endpointProvider = { "https://cells.example.test/api" },
            credentialsProvider = { S3Credentials("access-token", "gateway-secret") },
            fileSystem = fileSystem,
            config = fixedDateConfig(),
        )

        client.upload(
            path = uploadPath,
            node = cellNode(path = "folder/a file.txt"),
            onProgressUpdate = {},
        )

        val request = assertNotNull(capturedRequest)
        val authorization = assertNotNull(request.headers[HttpHeaders.Authorization])
        assertEquals(HttpMethod.Put, request.method)
        assertEquals("https://cells.example.test/api/io/folder/a%20file.txt", request.url.toString())
        assertEquals("cells.example.test", request.headers[HttpHeaders.Host])
        assertEquals("UNSIGNED-PAYLOAD", request.headers["x-amz-content-sha256"])
        assertEquals("20260701T120102Z", request.headers["x-amz-date"])
        assertEquals("true", request.headers["x-amz-meta-draft-mode"])
        assertEquals("node-uuid", request.headers["x-amz-meta-create-resource-uuid"])
        assertEquals("version-uuid", request.headers["x-amz-meta-create-version-id"])
        assertContains(authorization, "Credential=access-token/20260701/us-east-1/s3/aws4_request")
        assertContains(
            authorization,
            "SignedHeaders=host;x-amz-content-sha256;x-amz-date;" +
                    "x-amz-meta-create-resource-uuid;x-amz-meta-create-version-id;x-amz-meta-draft-mode",
        )
    }

    @Test
    fun givenRetryableServerResponses_whenUploading_thenRetriesWithFreshSignatures() = runTest {
        val fileSystem = FakeFileSystem()
        val uploadPath = "/upload.txt".toPath()
        val uploadBytes = "hello cells".encodeToByteArray()
        fileSystem.write(uploadPath) {
            write(uploadBytes)
        }
        var requestCount = 0
        var credentialsCount = 0
        val authorizationHeaders = mutableListOf<String>()
        val requestBodies = mutableListOf<ByteArray>()
        val progressUpdates = mutableListOf<Long>()
        val httpClient = HttpClient(
            MockEngine { request ->
                requestCount++
                authorizationHeaders += assertNotNull(request.headers[HttpHeaders.Authorization])
                requestBodies += request.body.toByteArray()
                respond(
                    content = when (requestCount) {
                        1 -> "<Error><Code>RequestTimeout</Code></Error>"
                        else -> ""
                    },
                    status = when (requestCount) {
                        1 -> HttpStatusCode.BadRequest
                        2 -> HttpStatusCode.ServiceUnavailable
                        else -> HttpStatusCode.OK
                    },
                )
            }
        )
        val client = CellsS3Client(
            httpClient = httpClient,
            endpointProvider = { "https://cells.example.test" },
            credentialsProvider = {
                credentialsCount++
                S3Credentials("access-token-$credentialsCount", "gateway-secret")
            },
            fileSystem = fileSystem,
            config = fixedDateConfig(),
        )

        client.upload(uploadPath, cellNode(path = "upload.txt")) { progressUpdates += it }

        assertEquals(EXPECTED_ATTEMPTS, requestCount)
        assertEquals(EXPECTED_ATTEMPTS, credentialsCount)
        authorizationHeaders.forEachIndexed { index, authorization ->
            assertContains(authorization, "Credential=access-token-${index + 1}/")
        }
        requestBodies.forEach { assertTrue(it.contentEquals(uploadBytes)) }
        assertEquals(uploadBytes.size.toLong(), progressUpdates.last())
        assertTrue(progressUpdates.zipWithNext().all { (previous, next) -> next > previous })
    }

    @Test
    fun givenClientErrorResponse_whenUploading_thenDoesNotRetry() = runTest {
        val fileSystem = FakeFileSystem()
        val uploadPath = "/upload.txt".toPath()
        fileSystem.write(uploadPath) {
            write("hello cells".encodeToByteArray())
        }
        var requestCount = 0
        val client = CellsS3Client(
            httpClient = HttpClient(
                MockEngine {
                    requestCount++
                    respond(content = "", status = HttpStatusCode.Forbidden)
                }
            ),
            endpointProvider = { "https://cells.example.test" },
            credentialsProvider = { S3Credentials("access-token", "gateway-secret") },
            fileSystem = fileSystem,
            config = fixedDateConfig(),
        )

        assertFailsWith<okio.IOException> {
            client.upload(uploadPath, cellNode(path = "upload.txt"), onProgressUpdate = {})
        }

        assertEquals(1, requestCount)
    }

    @Test
    fun givenNetworkFailures_whenUploading_thenRetriesAndSucceeds() = runTest {
        val fileSystem = FakeFileSystem()
        val uploadPath = "/upload.txt".toPath()
        fileSystem.write(uploadPath) {
            write("hello cells".encodeToByteArray())
        }
        var requestCount = 0
        val client = CellsS3Client(
            httpClient = HttpClient(
                MockEngine {
                    requestCount++
                    if (requestCount < EXPECTED_ATTEMPTS) throw IOException("connection lost")
                    respond(content = "", status = HttpStatusCode.OK)
                }
            ),
            endpointProvider = { "https://cells.example.test" },
            credentialsProvider = { S3Credentials("access-token", "gateway-secret") },
            fileSystem = fileSystem,
            config = fixedDateConfig(),
        )

        client.upload(uploadPath, cellNode(path = "upload.txt"), onProgressUpdate = {})

        assertEquals(EXPECTED_ATTEMPTS, requestCount)
    }

    @Test
    fun givenUploadSourceFailure_whenUploading_thenDoesNotRetry() = runTest {
        val delegateFileSystem = FakeFileSystem()
        val uploadPath = "/upload.txt".toPath()
        delegateFileSystem.write(uploadPath) {
            write("hello cells".encodeToByteArray())
        }
        val failingFileSystem = object : ForwardingFileSystem(delegateFileSystem) {
            override fun source(file: okio.Path): Source = throw okio.IOException("read failed")
        }
        var requestCount = 0
        val client = CellsS3Client(
            httpClient = HttpClient(
                MockEngine { request ->
                    requestCount++
                    (request.body as OutgoingContent.WriteChannelContent).writeTo(ByteChannel())
                    respond(content = "", status = HttpStatusCode.OK)
                }
            ),
            endpointProvider = { "https://cells.example.test" },
            credentialsProvider = { S3Credentials("access-token", "gateway-secret") },
            fileSystem = failingFileSystem,
            config = fixedDateConfig(),
        )

        val exception = assertFailsWith<okio.IOException> {
            client.upload(uploadPath, cellNode(path = "upload.txt"), onProgressUpdate = {})
        }

        assertContains(exception.message.orEmpty(), "upload source")
        assertEquals(1, requestCount)
    }

    @Test
    fun givenEmbeddedInternalError_whenCompletingMultipartUpload_thenRetriesOnlyCompletion() = runTest {
        val fileSystem = FakeFileSystem()
        val uploadPath = "/upload.txt".toPath()
        fileSystem.write(uploadPath) {
            write("multipart".encodeToByteArray())
        }
        var createCount = 0
        var partCount = 0
        var completionCount = 0
        val httpClient = HttpClient(
            MockEngine { request ->
                when {
                    request.method == HttpMethod.Post && request.url.parameters.names().contains("uploads") -> {
                        createCount++
                        respond(
                            content = "<InitiateMultipartUploadResult><UploadId>upload-id</UploadId>" +
                                    "</InitiateMultipartUploadResult>",
                            status = HttpStatusCode.OK,
                        )
                    }

                    request.method == HttpMethod.Put -> {
                        partCount++
                        respond(
                            content = "",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ETag, "etag-1"),
                        )
                    }

                    request.method == HttpMethod.Post && request.url.parameters["uploadId"] != null -> {
                        completionCount++
                        respond(
                            content = when (completionCount) {
                                1 -> "<?xml version=\"1.0\"?><Error><Code>InternalError</Code></Error>"
                                2 -> ""
                                else -> "<CompleteMultipartUploadResult/>"
                            },
                            status = HttpStatusCode.OK,
                        )
                    }

                    else -> error("Unexpected request: ${request.method} ${request.url}")
                }
            }
        )
        val client = CellsS3Client(
            httpClient = httpClient,
            endpointProvider = { "https://cells.example.test" },
            credentialsProvider = { S3Credentials("access-token", "gateway-secret") },
            fileSystem = fileSystem,
            config = fixedDateConfig(maxRegularUploadSize = 1),
        )

        client.upload(uploadPath, cellNode(path = "upload.txt"), onProgressUpdate = {})

        assertEquals(1, createCount)
        assertEquals(1, partCount)
        assertEquals(EXPECTED_ATTEMPTS, completionCount)
    }

    @Test
    fun givenEmbeddedValidationError_whenCompletingMultipartUpload_thenDoesNotRetry() = runTest {
        val fileSystem = FakeFileSystem()
        val uploadPath = "/upload.txt".toPath()
        fileSystem.write(uploadPath) {
            write("multipart".encodeToByteArray())
        }
        var completionCount = 0
        val httpClient = HttpClient(
            MockEngine { request ->
                when {
                    request.method == HttpMethod.Post && request.url.parameters.names().contains("uploads") -> respond(
                        content = "<InitiateMultipartUploadResult><UploadId>upload-id</UploadId>" +
                                "</InitiateMultipartUploadResult>",
                        status = HttpStatusCode.OK,
                    )

                    request.method == HttpMethod.Put -> respond(
                        content = "",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ETag, "etag-1"),
                    )

                    request.method == HttpMethod.Post && request.url.parameters["uploadId"] != null -> {
                        completionCount++
                        respond(
                            content = "<Error><Code>InvalidPart</Code></Error>",
                            status = HttpStatusCode.OK,
                        )
                    }

                    else -> error("Unexpected request: ${request.method} ${request.url}")
                }
            }
        )
        val client = CellsS3Client(
            httpClient = httpClient,
            endpointProvider = { "https://cells.example.test" },
            credentialsProvider = { S3Credentials("access-token", "gateway-secret") },
            fileSystem = fileSystem,
            config = fixedDateConfig(maxRegularUploadSize = 1),
        )

        val exception = assertFailsWith<okio.IOException> {
            client.upload(uploadPath, cellNode(path = "upload.txt"), onProgressUpdate = {})
        }

        assertContains(exception.message.orEmpty(), "InvalidPart")
        assertEquals(1, completionCount)
    }

    @Test
    fun givenDownloadResponse_whenDownloading_thenReportsProgress() = runTest {
        val payload = ByteArray(TEST_DOWNLOAD_SIZE) { it.toByte() }
        val progressUpdates = mutableListOf<Long>()
        val sink = okio.Buffer()
        val client = CellsS3Client(
            httpClient = HttpClient(MockEngine { respond(content = payload, status = HttpStatusCode.OK) }),
            endpointProvider = { "https://cells.example.test" },
            credentialsProvider = { S3Credentials("access-token", "gateway-secret") },
            config = fixedDateConfig(),
        )

        client.download("download.txt", sink) { progressUpdates += it }

        assertEquals(TEST_DOWNLOAD_SIZE.toLong(), progressUpdates.last())
        assertTrue(progressUpdates.zipWithNext().all { (previous, next) -> next > previous })
    }

    @Test
    fun givenOpenDownloadResponse_whenDownloading_thenStreamsBeforeResponseCompletes() = runTest {
        val firstChunk = ByteArray(TEST_STREAM_CHUNK_SIZE) { it.toByte() }
        val secondChunk = ByteArray(TEST_STREAM_CHUNK_SIZE) { (it + TEST_STREAM_CHUNK_SIZE).toByte() }
        val responseChannel = ByteChannel(autoFlush = true)
        val firstChunkCopied = CompletableDeferred<Unit>()
        val sink = okio.Buffer()
        val client = CellsS3Client(
            httpClient = HttpClient(MockEngine { respond(content = responseChannel, status = HttpStatusCode.OK) }),
            endpointProvider = { "https://cells.example.test" },
            credentialsProvider = { S3Credentials("access-token", "gateway-secret") },
            config = fixedDateConfig(),
        )

        val download = launch {
            client.download("download.txt", sink) { downloaded ->
                if (downloaded >= firstChunk.size) {
                    firstChunkCopied.complete(Unit)
                }
            }
        }

        responseChannel.writeFully(firstChunk)
        withContext(Dispatchers.Default) {
            withTimeout(STREAM_ASSERTION_TIMEOUT_MILLIS) {
                firstChunkCopied.await()
            }
        }

        responseChannel.writeFully(secondChunk)
        responseChannel.close()
        download.join()

        assertContentEquals(firstChunk + secondChunk, sink.readByteArray())
    }

    private fun cellNode(path: String): CellNodeDTO = CellNodeDTO(
        uuid = "node-uuid",
        versionId = "version-uuid",
        path = path,
        modified = null,
        size = null,
        contentUrl = null,
        contentUrlExpiresAt = null,
        contentHash = null,
        mimeType = null,
        ownerUserId = null,
        userHandle = null,
        conversationId = null,
        publicLinkId = null,
    )

    private fun fixedDateConfig(): CellsS3ClientConfig = CellsS3ClientConfig(
        dateProvider = { AwsSigningDate(date = "20260701", dateTime = "20260701T120102Z") },
    )

    private fun fixedDateConfig(maxRegularUploadSize: Long): CellsS3ClientConfig = CellsS3ClientConfig(
        dateProvider = { AwsSigningDate(date = "20260701", dateTime = "20260701T120102Z") },
        maxRegularUploadSize = maxRegularUploadSize,
    )

    private companion object {
        const val EXPECTED_ATTEMPTS = 3
        const val TEST_DOWNLOAD_SIZE = 20 * 1024
        const val TEST_STREAM_CHUNK_SIZE = 1024
        const val STREAM_ASSERTION_TIMEOUT_MILLIS = 5_000L
    }
}
