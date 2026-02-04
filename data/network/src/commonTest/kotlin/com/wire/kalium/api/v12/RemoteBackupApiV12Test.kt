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
package com.wire.kalium.api.v12

import com.wire.kalium.mocks.responses.RemoteBackupResponseJson
import com.wire.kalium.network.api.base.authenticated.remoteBackup.RemoteBackupProtoMapper
import com.wire.kalium.network.api.v12.authenticated.RemoteBackupApiV12
import com.wire.kalium.network.networkContainer.KaliumUserAgentProvider
import com.wire.kalium.network.serialization.XProtoBuf
import com.wire.kalium.network.serialization.xprotobuf
import com.wire.kalium.network.tools.KtxSerializer
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HeadersImpl
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import okio.Buffer
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class RemoteBackupApiV12Test {

    @BeforeTest
    fun setup() {
        KaliumUserAgentProvider.setUserAgent("test/useragent")
    }

    // region syncMessages tests

    @Test
    fun givenSyncMessagesRequest_whenInvoking_thenShouldUseCorrectEndpointAndMethod() = runTest {
        val request = RemoteBackupResponseJson.validSyncRequest
        var capturedMethod: HttpMethod? = null
        var capturedPath: String? = null

        val httpClient = createMockHttpClient(
            responseBody = "",
            statusCode = HttpStatusCode.OK
        ) { requestData ->
            capturedMethod = requestData.method
            capturedPath = requestData.url.encodedPath
        }

        val api = RemoteBackupApiV12(httpClient)
        api.syncMessages(request)

        assertEquals(HttpMethod.Post, capturedMethod)
        assertEquals("/backup/messages", capturedPath)
    }

    @Test
    fun givenSyncMessagesRequest_whenInvoking_thenShouldSerializeBodyCorrectly() = runTest {
        val request = RemoteBackupResponseJson.validSyncRequest
        var capturedBody: ByteArray? = null

        val httpClient = createMockHttpClient(
            responseBody = ByteArray(0),
            statusCode = HttpStatusCode.OK
        ) { requestData ->
            val body = requestData.body
            if (body is OutgoingContent.ByteArrayContent) {
                capturedBody = body.bytes()
            }
        }

        val api = RemoteBackupApiV12(httpClient)
        api.syncMessages(request)

        val expectedBytes = RemoteBackupProtoMapper().encodeSyncRequest(request)
        assertContentEquals(expectedBytes, checkNotNull(capturedBody))
    }

    @Test
    fun givenSyncMessagesRequest_whenSuccessful_thenShouldReturnSuccess() = runTest {
        val request = RemoteBackupResponseJson.validSyncRequest

        val httpClient = createMockHttpClient(
            responseBody = "",
            statusCode = HttpStatusCode.OK
        )

        val api = RemoteBackupApiV12(httpClient)
        val result = api.syncMessages(request)

        assertTrue(result.isSuccessful())
    }

    // endregion

    // region fetchMessages tests

    @Test
    fun givenFetchMessagesRequest_whenInvoking_thenShouldUseCorrectEndpointAndMethod() = runTest {
        var capturedMethod: HttpMethod? = null
        var capturedPath: String? = null

        val httpClient = createMockHttpClient(
            responseBody = RemoteBackupProtoMapper().encodeFetchResponse(RemoteBackupResponseJson.validFetchResponse),
            statusCode = HttpStatusCode.OK
        ) { requestData ->
            capturedMethod = requestData.method
            capturedPath = requestData.url.encodedPath
        }

        val api = RemoteBackupApiV12(httpClient)
        api.fetchMessages(user = TEST_USER_ID, size = 100)

        assertEquals(HttpMethod.Get, capturedMethod)
        assertEquals("/backup/messages", capturedPath)
    }

    @Test
    fun givenFetchMessagesRequest_whenInvoking_thenShouldIncludeRequiredQueryParams() = runTest {
        var capturedUserParam: String? = null
        var capturedSizeParam: String? = null

        val httpClient = createMockHttpClient(
            responseBody = RemoteBackupProtoMapper().encodeFetchResponse(RemoteBackupResponseJson.validFetchResponse),
            statusCode = HttpStatusCode.OK
        ) { requestData ->
            capturedUserParam = requestData.url.parameters["user"]
            capturedSizeParam = requestData.url.parameters["size"]
        }

        val api = RemoteBackupApiV12(httpClient)
        api.fetchMessages(user = TEST_USER_ID, size = 50)

        assertEquals(TEST_USER_ID, capturedUserParam)
        assertEquals("50", capturedSizeParam)
    }

    @Test
    fun givenFetchMessagesRequestWithOptionalParams_whenInvoking_thenShouldIncludeOptionalQueryParams() = runTest {
        var capturedSinceParam: String? = null
        var capturedConversationParam: String? = null
        var capturedPaginationTokenParam: String? = null

        val httpClient = createMockHttpClient(
            responseBody = RemoteBackupProtoMapper().encodeFetchResponse(RemoteBackupResponseJson.validFetchResponse),
            statusCode = HttpStatusCode.OK
        ) { requestData ->
            capturedSinceParam = requestData.url.parameters["since"]
            capturedConversationParam = requestData.url.parameters["conversation"]
            capturedPaginationTokenParam = requestData.url.parameters["pagination_token"]
        }

        val api = RemoteBackupApiV12(httpClient)
        api.fetchMessages(
            user = TEST_USER_ID,
            since = 1234567890L,
            conversation = TEST_CONVERSATION_ID,
            paginationToken = "next-page-token",
            size = 100
        )

        assertEquals("1234567890", capturedSinceParam)
        assertEquals(TEST_CONVERSATION_ID, capturedConversationParam)
        assertEquals("next-page-token", capturedPaginationTokenParam)
    }

    @Test
    fun givenFetchMessagesRequestWithoutOptionalParams_whenInvoking_thenShouldNotIncludeOptionalQueryParams() = runTest {
        var capturedSinceParam: String? = null
        var capturedConversationParam: String? = null
        var capturedPaginationTokenParam: String? = null

        val httpClient = createMockHttpClient(
            responseBody = RemoteBackupProtoMapper().encodeFetchResponse(RemoteBackupResponseJson.validFetchResponse),
            statusCode = HttpStatusCode.OK
        ) { requestData ->
            capturedSinceParam = requestData.url.parameters["since"]
            capturedConversationParam = requestData.url.parameters["conversation"]
            capturedPaginationTokenParam = requestData.url.parameters["pagination_token"]
        }

        val api = RemoteBackupApiV12(httpClient)
        api.fetchMessages(user = TEST_USER_ID, size = 100)

        assertNull(capturedSinceParam)
        assertNull(capturedConversationParam)
        assertNull(capturedPaginationTokenParam)
    }

    @Test
    fun givenFetchMessagesRequest_whenSuccessful_thenShouldDeserializeResponseCorrectly() = runTest {
        val expectedResponse = RemoteBackupResponseJson.validFetchResponse
        val httpClient = createMockHttpClient(
            responseBody = RemoteBackupProtoMapper().encodeFetchResponse(expectedResponse),
            statusCode = HttpStatusCode.OK
        )

        val api = RemoteBackupApiV12(httpClient)
        val result = api.fetchMessages(user = TEST_USER_ID, size = 100)

        assertTrue(result.isSuccessful())
        assertEquals(expectedResponse, result.value)
    }

    // endregion

    // region deleteMessages tests

    @Test
    fun givenDeleteMessagesRequest_whenInvoking_thenShouldUseCorrectEndpointAndMethod() = runTest {
        var capturedMethod: HttpMethod? = null
        var capturedPath: String? = null

        val httpClient = createMockHttpClient(
            responseBody = RemoteBackupResponseJson.validDeleteResponse.rawJson.encodeToByteArray(),
            statusCode = HttpStatusCode.OK,
            contentType = ContentType.Application.Json
        ) { requestData ->
            capturedMethod = requestData.method
            capturedPath = requestData.url.encodedPath
        }

        val api = RemoteBackupApiV12(httpClient)
        api.deleteMessages(userId = TEST_USER_ID)

        assertEquals(HttpMethod.Delete, capturedMethod)
        assertEquals("/backup/messages", capturedPath)
    }

    @Test
    fun givenDeleteMessagesRequestWithAllParams_whenInvoking_thenShouldIncludeAllQueryParams() = runTest {
        var capturedUserIdParam: String? = null
        var capturedConversationIdParam: String? = null
        var capturedBeforeParam: String? = null

        val httpClient = createMockHttpClient(
            responseBody = RemoteBackupResponseJson.validDeleteResponse.rawJson.encodeToByteArray(),
            statusCode = HttpStatusCode.OK,
            contentType = ContentType.Application.Json
        ) { requestData ->
            capturedUserIdParam = requestData.url.parameters["user_id"]
            capturedConversationIdParam = requestData.url.parameters["conversation_id"]
            capturedBeforeParam = requestData.url.parameters["before"]
        }

        val api = RemoteBackupApiV12(httpClient)
        api.deleteMessages(
            userId = TEST_USER_ID,
            conversationId = TEST_CONVERSATION_ID,
            before = 9876543210L
        )

        assertEquals(TEST_USER_ID, capturedUserIdParam)
        assertEquals(TEST_CONVERSATION_ID, capturedConversationIdParam)
        assertEquals("9876543210", capturedBeforeParam)
    }

    @Test
    fun givenDeleteMessagesRequestWithNoParams_whenInvoking_thenShouldNotIncludeQueryParams() = runTest {
        var capturedUserIdParam: String? = null
        var capturedConversationIdParam: String? = null
        var capturedBeforeParam: String? = null

        val httpClient = createMockHttpClient(
            responseBody = RemoteBackupResponseJson.validDeleteResponse.rawJson.encodeToByteArray(),
            statusCode = HttpStatusCode.OK,
            contentType = ContentType.Application.Json
        ) { requestData ->
            capturedUserIdParam = requestData.url.parameters["user_id"]
            capturedConversationIdParam = requestData.url.parameters["conversation_id"]
            capturedBeforeParam = requestData.url.parameters["before"]
        }

        val api = RemoteBackupApiV12(httpClient)
        api.deleteMessages()

        assertNull(capturedUserIdParam)
        assertNull(capturedConversationIdParam)
        assertNull(capturedBeforeParam)
    }

    @Test
    fun givenDeleteMessagesRequest_whenSuccessful_thenShouldDeserializeResponseCorrectly() = runTest {
        val expectedResponse = RemoteBackupResponseJson.validDeleteResponse.serializableData
        val httpClient = createMockHttpClient(
            responseBody = RemoteBackupResponseJson.validDeleteResponse.rawJson.encodeToByteArray(),
            statusCode = HttpStatusCode.OK,
            contentType = ContentType.Application.Json
        )

        val api = RemoteBackupApiV12(httpClient)
        val result = api.deleteMessages(userId = TEST_USER_ID)

        assertTrue(result.isSuccessful())
        assertEquals(expectedResponse.deletedCount, result.value.deletedCount)
    }

    // endregion

    // region uploadStateBackup tests

    @Test
    fun givenUploadStateBackupRequest_whenInvoking_thenShouldUseCorrectEndpointAndMethod() = runTest {
        var capturedMethod: HttpMethod? = null
        var capturedPath: String? = null

        val httpClient = createMockHttpClient(
            responseBody = "",
            statusCode = HttpStatusCode.OK
        ) { requestData ->
            capturedMethod = requestData.method
            capturedPath = requestData.url.encodedPath
        }

        val api = RemoteBackupApiV12(httpClient)
        val backupData = "test backup data".encodeToByteArray()
        api.uploadStateBackup(
            userId = TEST_USER_ID,
            backupDataSource = { Buffer().write(backupData) },
            backupSize = backupData.size.toLong()
        )

        assertEquals(HttpMethod.Post, capturedMethod)
        assertEquals("/backup/state", capturedPath)
    }

    @Test
    fun givenUploadStateBackupRequest_whenInvoking_thenShouldIncludeUserIdQueryParam() = runTest {
        var capturedUserIdParam: String? = null

        val httpClient = createMockHttpClient(
            responseBody = "",
            statusCode = HttpStatusCode.OK
        ) { requestData ->
            capturedUserIdParam = requestData.url.parameters["user_id"]
        }

        val api = RemoteBackupApiV12(httpClient)
        val backupData = "test backup data".encodeToByteArray()
        api.uploadStateBackup(
            userId = TEST_USER_ID,
            backupDataSource = { Buffer().write(backupData) },
            backupSize = backupData.size.toLong()
        )

        assertEquals(TEST_USER_ID, capturedUserIdParam)
    }

    @Test
    fun givenUploadStateBackupRequest_whenInvoking_thenShouldSetContentTypeToOctetStream() = runTest {
        var capturedContentType: ContentType? = null

        val httpClient = createMockHttpClient(
            responseBody = "",
            statusCode = HttpStatusCode.OK
        ) { requestData ->
            capturedContentType = requestData.body.contentType
        }

        val api = RemoteBackupApiV12(httpClient)
        val backupData = "test backup data".encodeToByteArray()
        api.uploadStateBackup(
            userId = TEST_USER_ID,
            backupDataSource = { Buffer().write(backupData) },
            backupSize = backupData.size.toLong()
        )

        assertEquals(ContentType.Application.OctetStream, capturedContentType)
    }

    @Test
    fun givenUploadStateBackupRequest_whenSuccessful_thenShouldReturnSuccess() = runTest {
        val httpClient = createMockHttpClient(
            responseBody = "",
            statusCode = HttpStatusCode.OK
        )

        val api = RemoteBackupApiV12(httpClient)
        val backupData = "test backup data".encodeToByteArray()
        val result = api.uploadStateBackup(
            userId = TEST_USER_ID,
            backupDataSource = { Buffer().write(backupData) },
            backupSize = backupData.size.toLong()
        )

        assertTrue(result.isSuccessful())
    }

    // endregion

    // region downloadStateBackup tests

    @Test
    fun givenDownloadStateBackupRequest_whenInvoking_thenShouldUseCorrectEndpointAndMethod() = runTest {
        var capturedMethod: HttpMethod? = null
        var capturedPath: String? = null

        val httpClient = createMockHttpClient(
            responseBody = "backup content".encodeToByteArray(),
            statusCode = HttpStatusCode.OK,
            contentType = ContentType.Text.Plain
        ) { requestData ->
            capturedMethod = requestData.method
            capturedPath = requestData.url.encodedPath
        }

        val api = RemoteBackupApiV12(httpClient)
        val sink = Buffer()
        api.downloadStateBackup(userId = TEST_USER_ID, tempFileSink = sink)

        assertEquals(HttpMethod.Get, capturedMethod)
        assertEquals("/backup/state", capturedPath)
    }

    @Test
    fun givenDownloadStateBackupRequest_whenInvoking_thenShouldIncludeUserIdQueryParam() = runTest {
        var capturedUserIdParam: String? = null

        val httpClient = createMockHttpClient(
            responseBody = "backup content".encodeToByteArray(),
            statusCode = HttpStatusCode.OK,
            contentType = ContentType.Text.Plain
        ) { requestData ->
            capturedUserIdParam = requestData.url.parameters["user_id"]
        }

        val api = RemoteBackupApiV12(httpClient)
        val sink = Buffer()
        api.downloadStateBackup(userId = TEST_USER_ID, tempFileSink = sink)

        assertEquals(TEST_USER_ID, capturedUserIdParam)
    }

    @Test
    fun givenDownloadStateBackupRequest_whenSuccessful_thenShouldWriteContentToSink() = runTest {
        val backupContent = "test backup content data"
        val httpClient = createMockHttpClient(
            responseBody = backupContent.encodeToByteArray(),
            statusCode = HttpStatusCode.OK,
            contentType = ContentType.Text.Plain
        )

        val api = RemoteBackupApiV12(httpClient)
        val sink = Buffer()
        val result = api.downloadStateBackup(userId = TEST_USER_ID, tempFileSink = sink)

        assertTrue(result.isSuccessful())
        assertEquals(backupContent, sink.readUtf8())
    }

    @Test
    fun givenDownloadStateBackupRequest_whenServerReturns404_thenShouldReturnError() = runTest {
        val httpClient = createMockHttpClient(
            responseBody = """{"code":404,"message":"No backup found","label":"not-found"}""".encodeToByteArray(),
            statusCode = HttpStatusCode.NotFound,
            contentType = ContentType.Application.Json
        )

        val api = RemoteBackupApiV12(httpClient)
        val sink = Buffer()
        val result = api.downloadStateBackup(userId = TEST_USER_ID, tempFileSink = sink)

        assertFalse(result.isSuccessful())
    }

    // endregion

    // region helper functions

    private fun createMockHttpClient(
        responseBody: ByteArray,
        statusCode: HttpStatusCode,
        contentType: ContentType = ContentType.Application.XProtoBuf,
        assertion: (io.ktor.client.request.HttpRequestData) -> Unit = {}
    ): HttpClient {
        val mockEngine = MockEngine { request ->
            assertion(request)
            respond(
                content = ByteReadChannel(responseBody),
                status = statusCode,
                headers = HeadersImpl(
                    mapOf(HttpHeaders.ContentType to listOf(contentType.toString()))
                )
            )
        }
        return HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(KtxSerializer.json)
                xprotobuf()
            }
            expectSuccess = false
        }
    }

    // endregion

    private companion object {
        const val TEST_USER_ID = "user-123-abc"
        const val TEST_CONVERSATION_ID = "conv-456-def"
    }
}
