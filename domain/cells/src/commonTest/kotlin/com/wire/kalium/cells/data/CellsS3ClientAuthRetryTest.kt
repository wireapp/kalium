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
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CellsS3ClientAuthRetryTest {

    @Test
    fun givenMultipartUpload_whenUploading_thenEveryRequestIsSignedWithTheSameAccessToken() = runTest {
        val (fileSystem, uploadPath) = createUploadFile(ByteArray(MULTIPART_UPLOAD_SIZE) { it.toByte() })
        val credentialsProvider = FakeS3CredentialsProvider()
        val authorizationHeaders = mutableListOf<String>()
        val client = createClient(
            httpClient = HttpClient(
                MockEngine { request ->
                    authorizationHeaders += assertNotNull(request.headers[HttpHeaders.Authorization])
                    respondToMultipartRequest(request)
                }
            ),
            fileSystem = fileSystem,
            credentialsProvider = credentialsProvider,
            config = fixedDateConfig(maxRegularUploadSize = 1, multipartChunkSize = MULTIPART_CHUNK_SIZE),
        )

        client.upload(uploadPath, cellNode(path = "upload.txt"), onProgressUpdate = {})

        // Creation, one request per part and completion.
        assertEquals(MULTIPART_REQUEST_COUNT, authorizationHeaders.size)
        assertEquals(0, credentialsProvider.refreshCount)
        authorizationHeaders.forEach { authorization ->
            assertContains(authorization, "Credential=$TEST_ACCESS_TOKEN/")
        }
    }

    @Test
    fun givenUnauthorizedPartResponse_whenUploadingMultipart_thenRefreshesOnceAndRetriesThePartOnce() = runTest {
        val (fileSystem, uploadPath) = createUploadFile(ByteArray(MULTIPART_UPLOAD_SIZE) { it.toByte() })
        val credentialsProvider = FakeS3CredentialsProvider()
        val partAuthorizationHeaders = mutableListOf<String>()
        val progressUpdates = mutableListOf<Long>()
        val client = createClient(
            httpClient = HttpClient(
                MockEngine { request ->
                    if (request.method == HttpMethod.Put) {
                        partAuthorizationHeaders += assertNotNull(request.headers[HttpHeaders.Authorization])
                        if (partAuthorizationHeaders.size == 1) {
                            respond(content = "", status = HttpStatusCode.Unauthorized)
                        } else {
                            respondToMultipartRequest(request)
                        }
                    } else {
                        respondToMultipartRequest(request)
                    }
                }
            ),
            fileSystem = fileSystem,
            credentialsProvider = credentialsProvider,
            config = fixedDateConfig(maxRegularUploadSize = 1, multipartChunkSize = MULTIPART_CHUNK_SIZE),
        )

        client.upload(uploadPath, cellNode(path = "upload.txt")) { progressUpdates += it }

        assertEquals(1, credentialsProvider.refreshCount)
        assertEquals(listOf(TEST_ACCESS_TOKEN), credentialsProvider.rejectedAccessKeyIds)
        // The rejected part is sent again, the remaining part is sent once.
        assertEquals(MULTIPART_PART_COUNT + 1, partAuthorizationHeaders.size)
        assertContains(partAuthorizationHeaders.first(), "Credential=$TEST_ACCESS_TOKEN/")
        partAuthorizationHeaders.drop(1).forEach { authorization ->
            assertContains(authorization, "Credential=${REFRESHED_TOKEN_PREFIX}1/")
        }
        assertEquals(listOf(MULTIPART_CHUNK_SIZE, MULTIPART_UPLOAD_SIZE.toLong()), progressUpdates)
    }

    @Test
    fun givenUnauthorizedResponse_whenUploading_thenProgressDoesNotMoveBackwardsDuringTheRetry() = runTest {
        val uploadBytes = ByteArray(TEST_UPLOAD_SIZE) { it.toByte() }
        val (fileSystem, uploadPath) = createUploadFile(uploadBytes)
        val credentialsProvider = FakeS3CredentialsProvider()
        val progressUpdates = mutableListOf<Long>()
        val requestBodies = mutableListOf<ByteArray>()
        val client = createClient(
            httpClient = HttpClient(
                MockEngine { request ->
                    requestBodies += request.body.toByteArray()
                    if (requestBodies.size == 1) {
                        respond(content = "", status = HttpStatusCode.Unauthorized)
                    } else {
                        respond(content = "", status = HttpStatusCode.OK)
                    }
                }
            ),
            fileSystem = fileSystem,
            credentialsProvider = credentialsProvider,
        )

        client.upload(uploadPath, cellNode(path = "upload.txt")) { progressUpdates += it }

        assertEquals(2, requestBodies.size)
        requestBodies.forEach { assertContentEquals(uploadBytes, it) }
        assertEquals(1, credentialsProvider.refreshCount)
        assertEquals(uploadBytes.size.toLong(), progressUpdates.last())
        assertTrue(progressUpdates.zipWithNext().all { (previous, next) -> next > previous })
    }

    @Test
    fun givenRepeatedUnauthorizedResponses_whenUploading_thenRefreshesOnceAndFails() = runTest {
        val (fileSystem, uploadPath) = createUploadFile("hello cells".encodeToByteArray())
        val credentialsProvider = FakeS3CredentialsProvider()
        var requestCount = 0
        val client = createClient(
            httpClient = HttpClient(
                MockEngine {
                    requestCount++
                    respond(content = "", status = HttpStatusCode.Unauthorized)
                }
            ),
            fileSystem = fileSystem,
            credentialsProvider = credentialsProvider,
        )

        val exception = assertFailsWith<okio.IOException> {
            client.upload(uploadPath, cellNode(path = "upload.txt"), onProgressUpdate = {})
        }

        assertContains(exception.message.orEmpty(), "401")
        assertEquals(2, requestCount)
        assertEquals(1, credentialsProvider.refreshCount)
    }

    @Test
    fun givenUnauthorizedResponse_whenDownloading_thenRefreshesOnceAndRetriesOnce() = runTest {
        val payload = "download".encodeToByteArray()
        val credentialsProvider = FakeS3CredentialsProvider()
        val authorizationHeaders = mutableListOf<String>()
        val sink = okio.Buffer()
        val client = createClient(
            httpClient = HttpClient(
                MockEngine { request ->
                    authorizationHeaders += assertNotNull(request.headers[HttpHeaders.Authorization])
                    if (authorizationHeaders.size == 1) {
                        respond(content = "", status = HttpStatusCode.Unauthorized)
                    } else {
                        respond(
                            content = payload,
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentLength, payload.size.toString()),
                        )
                    }
                }
            ),
            credentialsProvider = credentialsProvider,
        )

        client.download("download.txt", sink, onProgressUpdate = {})

        assertContentEquals(payload, sink.readByteArray())
        assertEquals(2, authorizationHeaders.size)
        assertEquals(1, credentialsProvider.refreshCount)
        assertContains(authorizationHeaders.first(), "Credential=$TEST_ACCESS_TOKEN/")
        assertContains(authorizationHeaders.last(), "Credential=${REFRESHED_TOKEN_PREFIX}1/")
    }

    private fun MockRequestHandleScope.respondToMultipartRequest(request: HttpRequestData): HttpResponseData = when {
        request.method == HttpMethod.Post && request.url.parameters.names().contains("uploads") -> respond(
            content = "<InitiateMultipartUploadResult><UploadId>upload-id</UploadId></InitiateMultipartUploadResult>",
            status = HttpStatusCode.OK,
        )

        request.method == HttpMethod.Put -> respond(
            content = "",
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ETag, "etag-${request.url.parameters["partNumber"]}"),
        )

        request.method == HttpMethod.Post && request.url.parameters["uploadId"] != null -> respond(
            content = "<CompleteMultipartUploadResult/>",
            status = HttpStatusCode.OK,
        )

        else -> error("Unexpected request: ${request.method} ${request.url}")
    }

    private companion object {
        const val MULTIPART_UPLOAD_SIZE = 8
        const val MULTIPART_CHUNK_SIZE = 4L
        const val MULTIPART_PART_COUNT = 2
        const val MULTIPART_REQUEST_COUNT = MULTIPART_PART_COUNT + 2
        const val TEST_UPLOAD_SIZE = 20 * 1024
    }
}
