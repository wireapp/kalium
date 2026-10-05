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

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.IOException
import okio.Path
import okio.Source
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CellsS3ClientMultipartAbortTest {

    @Test
    fun givenSourceReadFailure_whenUploadingMultipart_thenUploadIsAborted() = runTest {
        val (fileSystem, uploadPath) = createMultipartUploadFile()
        val failingFileSystem = object : ForwardingFileSystem(fileSystem) {
            override fun source(file: Path): Source = throw IOException("read failed")
        }
        val recorder = MultipartRecorder()
        val client = multipartClient(recorder = recorder, fileSystem = failingFileSystem)

        val exception = assertFailsWith<IOException> {
            client.upload(uploadPath, cellNode(path = "upload.txt"), onProgressUpdate = {})
        }

        assertContains(exception.message.orEmpty(), "read failed")
        assertEquals(listOf(TEST_UPLOAD_ID), recorder.abortedUploadIds)
        assertTrue(recorder.partNumbers.isEmpty())
        assertEquals(0, recorder.completeCount)
    }

    @Test
    fun givenPartUploadFailure_whenUploadingMultipart_thenUploadIsAborted() = runTest {
        val (fileSystem, uploadPath) = createMultipartUploadFile()
        val recorder = MultipartRecorder()
        val client = multipartClient(
            recorder = recorder,
            fileSystem = fileSystem,
            partStatus = { partNumber ->
                if (partNumber == 2) HttpStatusCode.Forbidden else HttpStatusCode.OK
            },
        )

        val exception = assertFailsWith<IOException> {
            client.upload(uploadPath, cellNode(path = "upload.txt"), onProgressUpdate = {})
        }

        assertContains(exception.message.orEmpty(), "Upload multipart part failed: 403")
        assertEquals(listOf(1, 2), recorder.partNumbers)
        assertEquals(listOf(TEST_UPLOAD_ID), recorder.abortedUploadIds)
        assertEquals(0, recorder.completeCount)
    }

    @Test
    fun givenAbortRequestFailure_whenUploadingMultipart_thenOriginalFailureIsReported() = runTest {
        val (fileSystem, uploadPath) = createMultipartUploadFile()
        val recorder = MultipartRecorder()
        val client = multipartClient(
            recorder = recorder,
            fileSystem = fileSystem,
            partStatus = { HttpStatusCode.Forbidden },
            abortStatus = HttpStatusCode.InternalServerError,
        )

        val exception = assertFailsWith<IOException> {
            client.upload(uploadPath, cellNode(path = "upload.txt"), onProgressUpdate = {})
        }

        assertContains(exception.message.orEmpty(), "Upload multipart part failed: 403")
        // The abort request is retryable, all of its attempts fail without replacing the upload failure.
        assertEquals(ABORT_ATTEMPTS, recorder.abortedUploadIds.size)
        assertTrue(recorder.abortedUploadIds.all { it == TEST_UPLOAD_ID })
    }

    @Test
    fun givenInitializationWithoutUploadId_whenUploadingMultipart_thenNoAbortIsAttempted() = runTest {
        val (fileSystem, uploadPath) = createMultipartUploadFile()
        val recorder = MultipartRecorder()
        val client = multipartClient(recorder = recorder, fileSystem = fileSystem, uploadId = null)

        val exception = assertFailsWith<IOException> {
            client.upload(uploadPath, cellNode(path = "upload.txt"), onProgressUpdate = {})
        }

        assertContains(exception.message.orEmpty(), "did not include an UploadId")
        assertTrue(recorder.abortedUploadIds.isEmpty())
        assertTrue(recorder.partNumbers.isEmpty())
    }

    @Test
    fun givenCancelledUploadJob_whenUploadingMultipart_thenAbortsBeforePropagatingOriginalCancellation() = runTest {
        val (fileSystem, uploadPath) = createMultipartUploadFile()
        val recorder = MultipartRecorder()
        val partStarted = CompletableDeferred<Unit>()
        val cancellation = CancellationException("upload cancelled")
        var failure: CancellationException? = null
        val client = createClient(
            httpClient = testHttpClient { request ->
                if (request.method == HttpMethod.Put) {
                    partStarted.complete(Unit)
                    awaitCancellation()
                } else {
                    respondToMultipartRequest(
                        request, recorder, TEST_UPLOAD_ID, { HttpStatusCode.OK },
                        HttpStatusCode.NoContent,
                    )
                }
            },
            fileSystem = fileSystem,
            config = fixedDateConfig(maxRegularUploadSize = 1, multipartChunkSize = CHUNK_SIZE),
        )

        val upload = launch {
            try {
                client.upload(uploadPath, cellNode(path = "upload.txt"), onProgressUpdate = {})
            } catch (cause: CancellationException) {
                failure = cause
                throw cause
            }
        }
        partStarted.await()
        upload.cancel(cancellation)
        upload.join()

        assertEquals(listOf(TEST_UPLOAD_ID), recorder.abortedUploadIds)
        assertTrue(generateSequence<Throwable>(assertNotNull(failure)) { it.cause }.any { it === cancellation })
        assertEquals(0, recorder.completeCount)
    }

    @Test
    fun givenStalledAbortRequest_whenUploadingFails_thenCleanupFinishesAndPreservesOriginalFailure() = runTest {
        val (fileSystem, uploadPath) = createMultipartUploadFile()
        val recorder = MultipartRecorder()
        var abortFinished = false
        val client = createClient(
            httpClient = testHttpClient { request ->
                if (request.method == HttpMethod.Delete) {
                    recorder.abortedUploadIds += assertNotNull(request.url.parameters["uploadId"])
                    try {
                        awaitCancellation()
                    } finally {
                        abortFinished = true
                    }
                } else {
                    respondToMultipartRequest(
                        request, recorder, TEST_UPLOAD_ID, { HttpStatusCode.Forbidden },
                        HttpStatusCode.NoContent,
                    )
                }
            },
            fileSystem = fileSystem,
            config = fixedDateConfig(maxRegularUploadSize = 1, multipartChunkSize = CHUNK_SIZE),
        )

        val exception = assertFailsWith<IOException> {
            client.upload(uploadPath, cellNode(path = "upload.txt"), onProgressUpdate = {})
        }

        assertContains(exception.message.orEmpty(), "Upload multipart part failed: 403")
        assertEquals(listOf(TEST_UPLOAD_ID), recorder.abortedUploadIds)
        assertTrue(abortFinished)
        assertEquals(ABORT_TIMEOUT_MILLIS, testScheduler.currentTime)
    }

    @Test
    fun givenUnauthorizedAbortResponse_whenUploadingFails_thenRefreshesOnceAndSignsAbortForTheSameObject() = runTest {
        val (fileSystem, uploadPath) = createMultipartUploadFile()
        val recorder = MultipartRecorder()
        val credentialsProvider = FakeS3CredentialsProvider()
        val abortAuthorizationHeaders = mutableListOf<String>()
        val client = createClient(
            httpClient = testHttpClient { request ->
                val abortStatus = if (request.method == HttpMethod.Delete) {
                    assertEquals("https://cells.example.test/io/folder/a%20file.txt?uploadId=$TEST_UPLOAD_ID", request.url.toString())
                    assertEquals("cells.example.test", request.headers[HttpHeaders.Host])
                    assertEquals("20260701T120102Z", request.headers["x-amz-date"])
                    abortAuthorizationHeaders += assertNotNull(request.headers[HttpHeaders.Authorization])
                    if (abortAuthorizationHeaders.size == 1) HttpStatusCode.Unauthorized else HttpStatusCode.NoContent
                } else {
                    HttpStatusCode.NoContent
                }
                respondToMultipartRequest(
                    request, recorder, TEST_UPLOAD_ID, { HttpStatusCode.Forbidden },
                    abortStatus,
                )
            },
            fileSystem = fileSystem,
            credentialsProvider = credentialsProvider,
            config = fixedDateConfig(maxRegularUploadSize = 1, multipartChunkSize = CHUNK_SIZE),
        )

        val exception = assertFailsWith<IOException> {
            client.upload(uploadPath, cellNode(path = "folder/a file.txt"), onProgressUpdate = {})
        }

        assertContains(exception.message.orEmpty(), "Upload multipart part failed: 403")
        assertEquals(listOf(TEST_UPLOAD_ID, TEST_UPLOAD_ID), recorder.abortedUploadIds)
        assertEquals(1, credentialsProvider.refreshCount)
        assertEquals(listOf(TEST_ACCESS_TOKEN), credentialsProvider.rejectedAccessKeyIds)
        assertContains(abortAuthorizationHeaders.first(), "Credential=$TEST_ACCESS_TOKEN/")
        assertContains(abortAuthorizationHeaders.last(), "Credential=${REFRESHED_TOKEN_PREFIX}1/")
        abortAuthorizationHeaders.forEach { assertContains(it, "SignedHeaders=host;x-amz-content-sha256;x-amz-date") }
    }

    // Keep the mock HTTP requests and cleanup deadline on the same virtual clock.
    private fun TestScope.testHttpClient(handler: MockRequestHandler): HttpClient = HttpClient(MockEngine) {
        engine {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler(handler)
        }
    }

    private fun createMultipartUploadFile() = createUploadFile(ByteArray(UPLOAD_SIZE) { it.toByte() })

    private fun TestScope.multipartClient(
        recorder: MultipartRecorder,
        fileSystem: FileSystem,
        uploadId: String? = TEST_UPLOAD_ID,
        partStatus: (Int) -> HttpStatusCode = { HttpStatusCode.OK },
        abortStatus: HttpStatusCode = HttpStatusCode.NoContent,
    ): CellsS3Client = createClient(
        httpClient = testHttpClient { request ->
            respondToMultipartRequest(request, recorder, uploadId, partStatus, abortStatus)
        },
        fileSystem = fileSystem,
        config = fixedDateConfig(maxRegularUploadSize = 1, multipartChunkSize = CHUNK_SIZE),
    )

    private fun MockRequestHandleScope.respondToMultipartRequest(
        request: HttpRequestData,
        recorder: MultipartRecorder,
        uploadId: String?,
        partStatus: (Int) -> HttpStatusCode,
        abortStatus: HttpStatusCode,
    ): HttpResponseData = when {
        request.method == HttpMethod.Post && request.url.parameters.names().contains("uploads") -> {
            respond(content = initiateMultipartUploadBody(uploadId), status = HttpStatusCode.OK)
        }

        request.method == HttpMethod.Put -> {
            val partNumber = assertNotNull(request.url.parameters["partNumber"]).toInt()
            recorder.partNumbers += partNumber
            respond(
                content = "",
                status = partStatus(partNumber),
                headers = headersOf(HttpHeaders.ETag, "etag-$partNumber"),
            )
        }

        request.method == HttpMethod.Delete -> {
            recorder.abortedUploadIds += assertNotNull(request.url.parameters["uploadId"])
            respond(content = "", status = abortStatus)
        }

        request.method == HttpMethod.Post -> {
            recorder.completeCount++
            respond(content = "<CompleteMultipartUploadResult/>", status = HttpStatusCode.OK)
        }

        else -> error("Unexpected request: ${request.method} ${request.url}")
    }

    private fun initiateMultipartUploadBody(uploadId: String?): String = if (uploadId == null) {
        "<InitiateMultipartUploadResult/>"
    } else {
        "<InitiateMultipartUploadResult><UploadId>$uploadId</UploadId></InitiateMultipartUploadResult>"
    }

    private class MultipartRecorder {
        var completeCount = 0
        val partNumbers = mutableListOf<Int>()
        val abortedUploadIds = mutableListOf<String>()
    }

    private companion object {
        const val TEST_UPLOAD_ID = "upload-id"
        const val UPLOAD_SIZE = 8
        const val CHUNK_SIZE = 4L
        const val ABORT_ATTEMPTS = 3
        const val ABORT_TIMEOUT_MILLIS = 30_000L
    }
}
