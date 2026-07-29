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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import okio.FileSystem
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
        val (fileSystem, uploadPath) = createUploadFile("hello cells".encodeToByteArray())
        val httpClient = HttpClient(
            MockEngine { request ->
                capturedRequest = request
                respond(content = "", status = HttpStatusCode.OK)
            }
        )
        val client = createClient(
            httpClient = httpClient,
            fileSystem = fileSystem,
            endpoint = "$TEST_ENDPOINT/api",
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
        val uploadBytes = "hello cells".encodeToByteArray()
        val (fileSystem, uploadPath) = createUploadFile(uploadBytes)
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
        val client = createClient(
            httpClient = httpClient,
            credentialsProvider = {
                credentialsCount++
                S3Credentials("access-token-$credentialsCount", "gateway-secret")
            },
            fileSystem = fileSystem,
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
        val (fileSystem, uploadPath) = createUploadFile("hello cells".encodeToByteArray())
        var requestCount = 0
        val client = createClient(
            httpClient = HttpClient(
                MockEngine {
                    requestCount++
                    respond(content = "", status = HttpStatusCode.Forbidden)
                }
            ),
            fileSystem = fileSystem,
        )

        assertFailsWith<okio.IOException> {
            client.upload(uploadPath, cellNode(path = "upload.txt"), onProgressUpdate = {})
        }

        assertEquals(1, requestCount)
    }

    @Test
    fun givenNetworkFailures_whenUploading_thenRetriesAndSucceeds() = runTest {
        val (fileSystem, uploadPath) = createUploadFile("hello cells".encodeToByteArray())
        var requestCount = 0
        val client = createClient(
            httpClient = HttpClient(
                MockEngine {
                    requestCount++
                    if (requestCount < EXPECTED_ATTEMPTS) throw IOException("connection lost")
                    respond(content = "", status = HttpStatusCode.OK)
                }
            ),
            fileSystem = fileSystem,
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
        val client = createClient(
            httpClient = HttpClient(
                MockEngine { request ->
                    requestCount++
                    (request.body as OutgoingContent.WriteChannelContent).writeTo(ByteChannel())
                    respond(content = "", status = HttpStatusCode.OK)
                }
            ),
            fileSystem = failingFileSystem,
        )

        val exception = assertFailsWith<okio.IOException> {
            client.upload(uploadPath, cellNode(path = "upload.txt"), onProgressUpdate = {})
        }

        assertContains(exception.message.orEmpty(), "upload source")
        assertEquals(1, requestCount)
    }

    @Test
    fun givenNamespacedMultipartResponses_whenUploading_thenParsesAttributedElementsAndXmlEntities() = runTest {
        val (fileSystem, uploadPath) = createUploadFile("multipart".encodeToByteArray())
        var partUploadId: String? = null
        var completionUploadId: String? = null
        val httpClient = HttpClient(
            MockEngine { request ->
                when {
                    request.method == HttpMethod.Post && request.url.parameters.names().contains("uploads") -> respond(
                        content = """
                            <s3:InitiateMultipartUploadResult xmlns:s3="http://s3.amazonaws.com/doc/2006-03-01/">
                                <s3:UploadId encoding="text"> upload&amp;&#45;id </s3:UploadId>
                            </s3:InitiateMultipartUploadResult>
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                    )

                    request.method == HttpMethod.Put -> {
                        partUploadId = request.url.parameters["uploadId"]
                        respond(
                            content = "",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ETag, "etag-1"),
                        )
                    }

                    request.method == HttpMethod.Post && request.url.parameters["uploadId"] != null -> {
                        completionUploadId = request.url.parameters["uploadId"]
                        respond(
                            content = """
                                <s3:CompleteMultipartUploadResult
                                    xmlns:s3="http://s3.amazonaws.com/doc/2006-03-01/"
                                    status="complete"
                                />
                            """.trimIndent(),
                            status = HttpStatusCode.OK,
                        )
                    }

                    else -> error("Unexpected request: ${request.method} ${request.url}")
                }
            }
        )
        val client = createClient(
            httpClient = httpClient,
            fileSystem = fileSystem,
            config = fixedDateConfig(maxRegularUploadSize = 1),
        )

        client.upload(uploadPath, cellNode(path = "upload.txt"), onProgressUpdate = {})

        assertEquals("upload&-id", partUploadId)
        assertEquals("upload&-id", completionUploadId)
    }

    @Test
    fun givenFileSpanningSeveralMultipartChunks_whenUploading_thenSendsSequentialExactPartsAndEscapedCompletionXml() = runTest {
        val uploadBytes = ByteArray(10) { it.toByte() }
        val (fileSystem, uploadPath) = createUploadFile(uploadBytes)
        val partNumbers = mutableListOf<Int>()
        val partBodies = mutableListOf<ByteArray>()
        val eTags = listOf("\"first&\"", "<second>", "third'")
        val progressUpdates = mutableListOf<Long>()
        var completionBody: String? = null
        val httpClient = HttpClient(
            MockEngine { request ->
                when {
                    request.method == HttpMethod.Post && request.url.parameters.names().contains("uploads") -> respond(
                        content = "<InitiateMultipartUploadResult><UploadId>upload-id</UploadId>" +
                                "</InitiateMultipartUploadResult>",
                        status = HttpStatusCode.OK,
                    )

                    request.method == HttpMethod.Put -> {
                        val partNumber = assertNotNull(request.url.parameters["partNumber"]).toInt()
                        assertEquals("upload-id", request.url.parameters["uploadId"])
                        partNumbers += partNumber
                        partBodies += request.body.toByteArray()
                        respond(
                            content = "",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ETag, eTags[partNumber - 1]),
                        )
                    }

                    request.method == HttpMethod.Post && request.url.parameters["uploadId"] != null -> {
                        assertEquals("upload-id", request.url.parameters["uploadId"])
                        completionBody = request.body.toByteArray().decodeToString()
                        respond(content = "<CompleteMultipartUploadResult/>", status = HttpStatusCode.OK)
                    }

                    else -> error("Unexpected request: ${request.method} ${request.url}")
                }
            }
        )
        val client = createClient(
            httpClient = httpClient,
            fileSystem = fileSystem,
            config = fixedDateConfig(
                maxRegularUploadSize = 1,
                multipartChunkSize = 4,
            ),
        )

        client.upload(uploadPath, cellNode(path = "upload.txt")) { progressUpdates += it }

        assertEquals(listOf(1, 2, 3), partNumbers)
        val expectedPartBodies = listOf(
            byteArrayOf(0, 1, 2, 3),
            byteArrayOf(4, 5, 6, 7),
            byteArrayOf(8, 9),
        )
        assertEquals(expectedPartBodies.size, partBodies.size)
        expectedPartBodies.forEachIndexed { index, expected ->
            assertContentEquals(expected, partBodies[index])
        }
        assertContentEquals(uploadBytes, partBodies.fold(ByteArray(0)) { result, part -> result + part })
        assertEquals(listOf(4L, 8L, 10L), progressUpdates)
        assertEquals(
            "<CompleteMultipartUpload>" +
                    "<Part><PartNumber>1</PartNumber><ETag>&quot;first&amp;&quot;</ETag></Part>" +
                    "<Part><PartNumber>2</PartNumber><ETag>&lt;second&gt;</ETag></Part>" +
                    "<Part><PartNumber>3</PartNumber><ETag>third&apos;</ETag></Part>" +
                    "</CompleteMultipartUpload>",
            completionBody,
        )
    }

    @Test
    fun givenNamespacedEmbeddedSlowDown_whenCreatingMultipartUpload_thenRetriesOnlyCreation() = runTest {
        val (fileSystem, uploadPath) = createUploadFile("multipart".encodeToByteArray())
        var createCount = 0
        var partCount = 0
        var completionCount = 0
        val httpClient = HttpClient(
            MockEngine { request ->
                when {
                    request.method == HttpMethod.Post && request.url.parameters.names().contains("uploads") -> {
                        createCount++
                        respond(
                            content = when (createCount) {
                                1 -> """
                                    <s3:Error xmlns:s3="http://s3.amazonaws.com/doc/2006-03-01/" status="503">
                                        <s3:Code source="server">Slow&#68;own</s3:Code>
                                    </s3:Error>
                                """.trimIndent()
                                else -> "<InitiateMultipartUploadResult><UploadId>upload-id</UploadId>" +
                                        "</InitiateMultipartUploadResult>"
                            },
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
                        respond(content = "<CompleteMultipartUploadResult/>", status = HttpStatusCode.OK)
                    }

                    else -> error("Unexpected request: ${request.method} ${request.url}")
                }
            }
        )
        val client = createClient(
            httpClient = httpClient,
            fileSystem = fileSystem,
            config = fixedDateConfig(maxRegularUploadSize = 1),
        )

        client.upload(uploadPath, cellNode(path = "upload.txt"), onProgressUpdate = {})

        assertEquals(2, createCount)
        assertEquals(1, partCount)
        assertEquals(1, completionCount)
    }

    @Test
    fun givenEmbeddedInternalError_whenCompletingMultipartUpload_thenRetriesOnlyCompletion() = runTest {
        val (fileSystem, uploadPath) = createUploadFile("multipart".encodeToByteArray())
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
                                1 -> """
                                    <s3:Error xmlns:s3="http://s3.amazonaws.com/doc/2006-03-01/" status="500">
                                        <s3:Code source="server">InternalError</s3:Code>
                                    </s3:Error>
                                """.trimIndent()
                                2 -> ""
                                else -> """
                                    <s3:CompleteMultipartUploadResult
                                        xmlns:s3="http://s3.amazonaws.com/doc/2006-03-01/"
                                        status="complete"
                                    />
                                """.trimIndent()
                            },
                            status = HttpStatusCode.OK,
                        )
                    }

                    else -> error("Unexpected request: ${request.method} ${request.url}")
                }
            }
        )
        val client = createClient(
            httpClient = httpClient,
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
        val (fileSystem, uploadPath) = createUploadFile("multipart".encodeToByteArray())
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
                            content = """
                                <s3:Error xmlns:s3="http://s3.amazonaws.com/doc/2006-03-01/" status="400">
                                    <s3:Code source="server">InvalidPart</s3:Code>
                                </s3:Error>
                            """.trimIndent(),
                            status = HttpStatusCode.OK,
                        )
                    }

                    else -> error("Unexpected request: ${request.method} ${request.url}")
                }
            }
        )
        val client = createClient(
            httpClient = httpClient,
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
        val client = createClient(
            httpClient = HttpClient(
                MockEngine {
                    respond(
                        content = payload,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentLength, payload.size.toString()),
                    )
                }
            ),
        )

        client.download("download.txt", sink) { progressUpdates += it }

        assertEquals(TEST_DOWNLOAD_SIZE.toLong(), progressUpdates.last())
        assertTrue(progressUpdates.zipWithNext().all { (previous, next) -> next > previous })
    }

    @Test
    fun givenRetryableStatusBeforeDownloadBody_whenDownloading_thenRetriesAndWritesPayloadOnce() = runTest {
        val payload = "download".encodeToByteArray()
        var requestCount = 0
        val sink = okio.Buffer()
        val client = createClient(
            httpClient = HttpClient(
                MockEngine {
                    requestCount++
                    if (requestCount == 1) {
                        respond(content = "", status = HttpStatusCode.ServiceUnavailable)
                    } else {
                        respond(
                            content = payload,
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentLength, payload.size.toString()),
                        )
                    }
                }
            ),
        )

        client.download("download.txt", sink, onProgressUpdate = {})

        assertContentEquals(payload, sink.readByteArray())
        assertEquals(2, requestCount)
    }

    @Test
    fun givenBodyFailureAfterBytes_whenDownloading_thenDoesNotRetryOrDuplicateBytes() = runTest {
        val payload = "partial".encodeToByteArray()
        var requestCount = 0
        val progressUpdates = mutableListOf<Long>()
        val firstChunkCopied = CompletableDeferred<Unit>()
        val responseChannel = ByteChannel(autoFlush = true)
        val readFailure = IOException("connection lost")
        val sink = okio.Buffer()
        val client = createClient(
            httpClient = HttpClient(
                MockEngine {
                    requestCount++
                    respond(content = responseChannel, status = HttpStatusCode.OK)
                }
            ),
        )

        var failure: Throwable? = null
        val download = launch {
            try {
                client.download("download.txt", sink) {
                    progressUpdates += it
                    firstChunkCopied.complete(Unit)
                }
            } catch (cause: Throwable) {
                failure = cause
            }
        }
        responseChannel.writeFully(payload)
        withContext(Dispatchers.Default) {
            withTimeout(STREAM_ASSERTION_TIMEOUT_MILLIS) {
                firstChunkCopied.await()
            }
        }
        responseChannel.close(readFailure)
        download.join()

        val exception = assertNotNull(failure)
        assertContains(exception.message.orEmpty(), "could not be copied")
        val preservedCause = assertNotNull(exception.cause)
        assertTrue(preservedCause is IOException)
        assertContains(preservedCause.message.orEmpty(), readFailure.message.orEmpty())
        assertContentEquals(payload, sink.readByteArray())
        assertEquals(listOf(payload.size.toLong()), progressUpdates)
        assertEquals(1, requestCount)
    }

    @Test
    fun givenBodyFailureBeforeBytes_whenDownloading_thenDoesNotRetry() = runTest {
        var requestCount = 0
        val readFailure = IOException("connection lost")
        val sink = okio.Buffer()
        val client = createClient(
            httpClient = HttpClient(
                MockEngine {
                    requestCount++
                    val responseChannel = ByteChannel(autoFlush = true)
                    responseChannel.close(readFailure)
                    respond(content = responseChannel, status = HttpStatusCode.OK)
                }
            ),
        )

        val exception = assertFailsWith<okio.IOException> {
            client.download("download.txt", sink, onProgressUpdate = {})
        }

        assertContains(exception.message.orEmpty(), "could not be copied")
        val preservedCause = assertNotNull(exception.cause)
        assertTrue(preservedCause is IOException)
        assertContains(preservedCause.message.orEmpty(), readFailure.message.orEmpty())
        assertEquals(0L, sink.size)
        assertEquals(1, requestCount)
    }

    @Test
    fun givenSinkWriteFailureAfterBytes_whenDownloading_thenDoesNotRetryOrDuplicateBytes() = runTest {
        val payload = "download".encodeToByteArray()
        var requestCount = 0
        val sinkFailure = okio.IOException("sink write failed")
        val destination = okio.Buffer()
        val failingSink = object : okio.ForwardingSink(destination) {
            private var writeCount = 0

            override fun write(source: okio.Buffer, byteCount: Long) {
                writeCount++
                if (writeCount == 1) {
                    super.write(source, PARTIAL_SINK_WRITE_SIZE)
                }
                throw sinkFailure
            }
        }
        val client = createClient(
            httpClient = HttpClient(
                MockEngine {
                    requestCount++
                    respond(content = payload, status = HttpStatusCode.OK)
                }
            ),
        )

        val exception = assertFailsWith<okio.IOException> {
            client.download("download.txt", failingSink, onProgressUpdate = {})
        }

        assertContains(exception.message.orEmpty(), "could not be copied")
        assertTrue(exception.cause === sinkFailure)
        assertContentEquals(payload.copyOf(PARTIAL_SINK_WRITE_SIZE.toInt()), destination.readByteArray())
        assertEquals(1, requestCount)
    }

    @Test
    fun givenCancellationDuringBodyCopy_whenDownloading_thenPropagatesSameCancellationWithoutRetrying() = runTest {
        val payload = "download".encodeToByteArray()
        val cancellation = CancellationException("download cancelled")
        var requestCount = 0
        val client = createClient(
            httpClient = HttpClient(
                MockEngine {
                    requestCount++
                    respond(content = payload, status = HttpStatusCode.OK)
                }
            ),
        )

        val exception = assertFailsWith<CancellationException> {
            client.download("download.txt", okio.Buffer()) {
                throw cancellation
            }
        }

        assertTrue(exception === cancellation)
        assertEquals(1, requestCount)
    }

    @Test
    fun givenMismatchedDownloadLengths_whenDownloading_thenFailsWithoutRetryingOrDuplicatingBody() = runTest {
        listOf("short".encodeToByteArray() to 1, "too long".encodeToByteArray() to -1).forEach { (payload, difference) ->
            val expectedLength = payload.size + difference
            var requestCount = 0
            val sink = okio.Buffer()
            val client = createClient(
                httpClient = HttpClient(
                    MockEngine {
                        requestCount++
                        respond(
                            content = payload,
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentLength, expectedLength.toString()),
                        )
                    }
                ),
            )

            val exception = assertFailsWith<okio.IOException> {
                client.download("download.txt", sink, onProgressUpdate = {})
            }

            assertContains(exception.message.orEmpty(), "expected $expectedLength bytes")
            assertContains(exception.message.orEmpty(), "received ${payload.size} bytes")
            assertContentEquals(payload, sink.readByteArray())
            assertEquals(1, requestCount)
        }
    }

    @Test
    fun givenMissingContentLength_whenDownloading_thenSucceedsWithoutValidation() = runTest {
        val payload = "download".encodeToByteArray()
        var requestCount = 0
        val sink = okio.Buffer()
        val client = createClient(
            httpClient = HttpClient(
                MockEngine {
                    requestCount++
                    respond(content = payload, status = HttpStatusCode.OK)
                }
            ),
        )

        client.download("download.txt", sink, onProgressUpdate = {})

        assertContentEquals(payload, sink.readByteArray())
        assertEquals(1, requestCount)
    }

    @Test
    fun givenInvalidContentLengths_whenDownloading_thenFailsWithoutRetrying() = runTest {
        val payload = "download".encodeToByteArray()
        listOf("invalid", "9223372036854775808", "-1").forEach { contentLength ->
            var requestCount = 0
            val sink = okio.Buffer()
            val client = createClient(
                httpClient = HttpClient(
                    MockEngine {
                        requestCount++
                        respond(
                            content = payload,
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentLength, contentLength),
                        )
                    }
                ),
            )

            val exception = assertFailsWith<okio.IOException> {
                client.download("download.txt", sink, onProgressUpdate = {})
            }

            assertContains(exception.message.orEmpty(), "invalid Content-Length: '$contentLength'")
            assertEquals(0L, sink.size)
            assertEquals(1, requestCount)
        }
    }

    @Test
    fun givenOpenDownloadResponse_whenDownloading_thenStreamsBeforeResponseCompletes() = runTest {
        val firstChunk = ByteArray(TEST_STREAM_CHUNK_SIZE) { it.toByte() }
        val secondChunk = ByteArray(TEST_STREAM_CHUNK_SIZE) { (it + TEST_STREAM_CHUNK_SIZE).toByte() }
        val responseChannel = ByteChannel(autoFlush = true)
        val firstChunkCopied = CompletableDeferred<Unit>()
        val sink = okio.Buffer()
        val client = createClient(
            httpClient = HttpClient(MockEngine { respond(content = responseChannel, status = HttpStatusCode.OK) }),
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

    private fun createClient(
        httpClient: HttpClient,
        fileSystem: FileSystem = FileSystem.SYSTEM,
        endpoint: String = TEST_ENDPOINT,
        credentialsProvider: suspend () -> S3Credentials = { TEST_CREDENTIALS },
        config: CellsS3ClientConfig = fixedDateConfig(),
    ): CellsS3Client = CellsS3Client(
        httpClient = httpClient,
        endpointProvider = { endpoint },
        credentialsProvider = credentialsProvider,
        fileSystem = fileSystem,
        config = config,
    )

    private fun createUploadFile(bytes: ByteArray): Pair<FakeFileSystem, okio.Path> {
        val fileSystem = FakeFileSystem()
        val path = "/upload.txt".toPath()
        fileSystem.write(path) {
            write(bytes)
        }
        return fileSystem to path
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

    private fun fixedDateConfig(
        maxRegularUploadSize: Long,
        multipartChunkSize: Long = DEFAULT_TEST_MULTIPART_CHUNK_SIZE,
    ): CellsS3ClientConfig = CellsS3ClientConfig(
        dateProvider = { AwsSigningDate(date = "20260701", dateTime = "20260701T120102Z") },
        maxRegularUploadSize = maxRegularUploadSize,
        multipartChunkSize = multipartChunkSize,
    )

    private companion object {
        const val TEST_ENDPOINT = "https://cells.example.test"
        const val EXPECTED_ATTEMPTS = 3
        const val TEST_DOWNLOAD_SIZE = 20 * 1024
        const val TEST_STREAM_CHUNK_SIZE = 1024
        const val STREAM_ASSERTION_TIMEOUT_MILLIS = 5_000L
        const val PARTIAL_SINK_WRITE_SIZE = 3L
        const val DEFAULT_TEST_MULTIPART_CHUNK_SIZE = 10 * 1024 * 1024L
        val TEST_CREDENTIALS = S3Credentials("access-token", "gateway-secret")
    }
}
