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
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
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

class CellsS3ClientMultipartAbortTest {

    @Test
    fun givenSuccessfulMultipartUpload_whenUploading_thenUploadIsNotAborted() = runTest {
        val (fileSystem, uploadPath) = createMultipartUploadFile()
        val recorder = MultipartRecorder()
        val client = multipartClient(recorder = recorder, fileSystem = fileSystem)

        client.upload(uploadPath, cellNode(path = "upload.txt"), onProgressUpdate = {})

        assertEquals(1, recorder.createCount)
        assertEquals(listOf(1, 2), recorder.partNumbers)
        assertEquals(1, recorder.completeCount)
        assertTrue(recorder.abortedUploadIds.isEmpty())
    }

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
    fun givenCompletionFailure_whenUploadingMultipart_thenUploadIsAborted() = runTest {
        val (fileSystem, uploadPath) = createMultipartUploadFile()
        val recorder = MultipartRecorder()
        val client = multipartClient(
            recorder = recorder,
            fileSystem = fileSystem,
            completeStatus = HttpStatusCode.BadRequest,
        )

        val exception = assertFailsWith<IOException> {
            client.upload(uploadPath, cellNode(path = "upload.txt"), onProgressUpdate = {})
        }

        assertContains(exception.message.orEmpty(), "Complete multipart upload failed: 400")
        assertEquals(1, recorder.completeCount)
        assertEquals(listOf(TEST_UPLOAD_ID), recorder.abortedUploadIds)
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

    private fun createMultipartUploadFile() = createUploadFile(ByteArray(UPLOAD_SIZE) { it.toByte() })

    private fun multipartClient(
        recorder: MultipartRecorder,
        fileSystem: FileSystem,
        uploadId: String? = TEST_UPLOAD_ID,
        partStatus: (Int) -> HttpStatusCode = { HttpStatusCode.OK },
        completeStatus: HttpStatusCode = HttpStatusCode.OK,
        abortStatus: HttpStatusCode = HttpStatusCode.NoContent,
    ): CellsS3Client = createClient(
        httpClient = HttpClient(
            MockEngine { request ->
                respondToMultipartRequest(request, recorder, uploadId, partStatus, completeStatus, abortStatus)
            }
        ),
        fileSystem = fileSystem,
        config = fixedDateConfig(maxRegularUploadSize = 1, multipartChunkSize = CHUNK_SIZE),
    )

    private fun MockRequestHandleScope.respondToMultipartRequest(
        request: HttpRequestData,
        recorder: MultipartRecorder,
        uploadId: String?,
        partStatus: (Int) -> HttpStatusCode,
        completeStatus: HttpStatusCode,
        abortStatus: HttpStatusCode,
    ): HttpResponseData = when {
        request.method == HttpMethod.Post && request.url.parameters.names().contains("uploads") -> {
            recorder.createCount++
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
            respond(content = "<CompleteMultipartUploadResult/>", status = completeStatus)
        }

        else -> error("Unexpected request: ${request.method} ${request.url}")
    }

    private fun initiateMultipartUploadBody(uploadId: String?): String = if (uploadId == null) {
        "<InitiateMultipartUploadResult/>"
    } else {
        "<InitiateMultipartUploadResult><UploadId>$uploadId</UploadId></InitiateMultipartUploadResult>"
    }

    private class MultipartRecorder {
        var createCount = 0
        var completeCount = 0
        val partNumbers = mutableListOf<Int>()
        val abortedUploadIds = mutableListOf<String>()
    }

    private companion object {
        const val TEST_UPLOAD_ID = "upload-id"
        const val UPLOAD_SIZE = 8
        const val CHUNK_SIZE = 4L
        const val ABORT_ATTEMPTS = 3
    }
}