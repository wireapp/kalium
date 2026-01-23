/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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

import com.wire.kalium.api.ApiTest
import com.wire.kalium.network.api.base.authenticated.backup.MessageSyncApi
import com.wire.kalium.network.api.v12.authenticated.MessageSyncApiV12
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.Source
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
internal class MessageSyncApiV12Test : ApiTest() {

    @Test
    fun givenValidStateBackupUploadRequest_whenUploadingStateBackup_thenRequestShouldBeConfiguredCorrectly() = runTest {
        // Given
        val fileSystem = FakeFileSystem()
        val userId = "test-user-id"
        val backupData = "encrypted-backup-data".encodeToByteArray()
        val backupDataSource = { getDummyDataSource(fileSystem, backupData) }
        val backupSize = backupData.size.toLong()

        val networkClient = mockAuthenticatedNetworkClient(
            responseBody = ByteArray(0),
            statusCode = HttpStatusCode.Created,
            assertion = {
                assertPost()
                assertQueryParameter("user_id", userId)
                assertAuthorizationHeaderExist()
                assertContentType(ContentType.Application.OctetStream)
            }
        )

        // When
        val messageSyncApi: MessageSyncApi = MessageSyncApiV12(networkClient.httpClient)
        val response = messageSyncApi.uploadStateBackup(userId, backupDataSource, backupSize)

        // Then
        assertTrue(response.isSuccessful())
    }

    @Test
    fun givenEmptyResponse_whenUploadingStateBackup_thenShouldAcceptEmptyResponse() = runTest {
        // Given
        val fileSystem = FakeFileSystem()
        val userId = "test-user-id"
        val backupData = "some-backup-data".encodeToByteArray()
        val backupDataSource = { getDummyDataSource(fileSystem, backupData) }
        val backupSize = backupData.size.toLong()

        val networkClient = mockAuthenticatedNetworkClient(
            responseBody = ByteArray(0),
            statusCode = HttpStatusCode.Created,
            assertion = {}
        )

        // When
        val messageSyncApi: MessageSyncApi = MessageSyncApiV12(networkClient.httpClient)
        val response = messageSyncApi.uploadStateBackup(userId, backupDataSource, backupSize)

        // Then - should not throw exception, accept empty response
        assertTrue(response.isSuccessful())
    }

    @Test
    fun givenInvalidRequest_whenUploadingStateBackup_thenShouldReturnError() = runTest {
        // Given
        val fileSystem = FakeFileSystem()
        val userId = "test-user-id"
        val backupData = "some-backup-data".encodeToByteArray()
        val backupDataSource = { getDummyDataSource(fileSystem, backupData) }
        val backupSize = backupData.size.toLong()

        val networkClient = mockAuthenticatedNetworkClient(
            responseBody = "Bad Request",
            statusCode = HttpStatusCode.BadRequest,
            assertion = {}
        )

        // When
        val messageSyncApi: MessageSyncApi = MessageSyncApiV12(networkClient.httpClient)
        val response = messageSyncApi.uploadStateBackup(userId, backupDataSource, backupSize)

        // Then
        assertTrue(response is com.wire.kalium.network.utils.NetworkResponse.Error)
    }

    @Test
    fun givenLargeBackup_whenUploadingStateBackup_thenRequestShouldBeConfiguredCorrectly() = runTest {
        // Given
        val fileSystem = FakeFileSystem()
        val userId = "large-backup-user"
        val backupData = ByteArray(1024 * 1024) { it.toByte() } // 1 MB backup
        val backupDataSource = { getDummyDataSource(fileSystem, backupData) }
        val backupSize = backupData.size.toLong()

        val networkClient = mockAuthenticatedNetworkClient(
            responseBody = ByteArray(0),
            statusCode = HttpStatusCode.Created,
            assertion = {
                assertPost()
                assertQueryParameter("user_id", userId)
                assertAuthorizationHeaderExist()
                assertContentType(ContentType.Application.OctetStream)
            }
        )

        // When
        val messageSyncApi: MessageSyncApi = MessageSyncApiV12(networkClient.httpClient)
        val response = messageSyncApi.uploadStateBackup(userId, backupDataSource, backupSize)

        // Then
        assertTrue(response.isSuccessful())
    }

    private fun getDummyDataSource(fileSystem: FakeFileSystem, dummyData: ByteArray): Source {
        val dummyPath = "backup-data-path".toPath()
        fileSystem.write(dummyPath) {
            write(dummyData)
        }
        return fileSystem.source(dummyPath)
    }

    @Test
    fun givenValidFetchMessagesRequest_whenFetchingMessages_thenRequestShouldBeConfiguredCorrectly() = runTest {
        // Given
        val userId = "user-id"
        val conversationId = "conv-id@domain.com"
        val since = 1234567890L
        val paginationToken = "msg-token-123"
        val size = 50

        val responseJson = """
            {
                "has_more": true,
                "conversations": {
                    "conv1@domain.com": {
                        "last_read": 1234567900,
                        "messages": [
                            {
                                "message_id": "msg-1",
                                "timestamp": "1234567890",
                                "payload": "{\"id\":\"msg-1\"}"
                            }
                        ]
                    }
                },
                "pagination_token": "next-token"
            }
        """.trimIndent()

        val networkClient = mockAuthenticatedNetworkClient(
            responseJson,
            statusCode = HttpStatusCode.OK,
            assertion = {}
        )

        // When
        val messageSyncApi: MessageSyncApi = MessageSyncApiV12(networkClient.httpClient)
        val response = messageSyncApi.fetchMessages(userId, since, conversationId, paginationToken, size)

        // Then
        assertTrue(response.isSuccessful())
        val data = (response as com.wire.kalium.network.utils.NetworkResponse.Success).value
        assertTrue(data.hasMore)
        assertTrue(data.conversations.containsKey("conv1@domain.com"))
        assertTrue(data.paginationToken == "next-token")
    }

    @Test
    fun givenEmptyConversations_whenFetchingMessages_thenShouldReturnEmptyResponse() = runTest {
        // Given
        val userId = "user-id"
        val responseJson = """
            {
                "has_more": false,
                "conversations": {}
            }
        """.trimIndent()

        val networkClient = mockAuthenticatedNetworkClient(
            responseJson,
            statusCode = HttpStatusCode.OK,
            assertion = {}
        )

        // When
        val messageSyncApi: MessageSyncApi = MessageSyncApiV12(networkClient.httpClient)
        val response = messageSyncApi.fetchMessages(userId, null, null, null, 100)

        // Then
        assertTrue(response.isSuccessful())
        val data = (response as com.wire.kalium.network.utils.NetworkResponse.Success).value
        assertTrue(!data.hasMore)
        assertTrue(data.conversations.isEmpty())
        assertTrue(data.paginationToken == null)
    }

    @Test
    fun givenMultipleConversations_whenFetchingMessages_thenShouldParseAllConversations() = runTest {
        // Given
        val userId = "user-id"
        val responseJson = """
            {
                "has_more": true,
                "conversations": {
                    "conv1@domain.com": {
                        "last_read": 1234567900,
                        "messages": [
                            {
                                "message_id": "msg-1",
                                "timestamp": "1234567890",
                                "payload": "{\"id\":\"msg-1\"}"
                            },
                            {
                                "message_id": "msg-2",
                                "timestamp": "1234567891",
                                "payload": "{\"id\":\"msg-2\"}"
                            }
                        ]
                    },
                    "conv2@domain.com": {
                        "messages": [
                            {
                                "message_id": "msg-3",
                                "timestamp": "1234567892",
                                "payload": "{\"id\":\"msg-3\"}"
                            }
                        ]
                    }
                },
                "pagination_token": "next-token"
            }
        """.trimIndent()

        val networkClient = mockAuthenticatedNetworkClient(
            responseJson,
            statusCode = HttpStatusCode.OK,
            assertion = {}
        )

        // When
        val messageSyncApi: MessageSyncApi = MessageSyncApiV12(networkClient.httpClient)
        val response = messageSyncApi.fetchMessages(userId, null, null, null, 100)

        // Then
        assertTrue(response.isSuccessful())
        val data = (response as com.wire.kalium.network.utils.NetworkResponse.Success).value
        assertTrue(data.conversations.size == 2)
        assertTrue(data.conversations["conv1@domain.com"]?.messages?.size == 2)
        assertTrue(data.conversations["conv1@domain.com"]?.lastRead == 1234567900L)
        assertTrue(data.conversations["conv2@domain.com"]?.messages?.size == 1)
        assertTrue(data.conversations["conv2@domain.com"]?.lastRead == null)
    }

    @Test
    fun givenNotFoundError_whenFetchingMessages_thenShouldReturnError() = runTest {
        // Given
        val userId = "user-id"
        val networkClient = mockAuthenticatedNetworkClient(
            """{"code":404,"message":"Not Found","label":"not-found"}""",
            statusCode = HttpStatusCode.NotFound,
            assertion = {}
        )

        // When
        val messageSyncApi: MessageSyncApi = MessageSyncApiV12(networkClient.httpClient)
        val response = messageSyncApi.fetchMessages(userId, null, null, null, 100)

        // Then
        assertTrue(response is com.wire.kalium.network.utils.NetworkResponse.Error)
    }

    @Test
    fun givenOnlyOptionalParameters_whenFetchingMessages_thenOnlyRequiredParametersShouldBeSent() = runTest {
        // Given
        val userId = "user-id"
        val size = 100
        val responseJson = """
            {
                "has_more": false,
                "conversations": {}
            }
        """.trimIndent()

        val networkClient = mockAuthenticatedNetworkClient(
            responseJson,
            statusCode = HttpStatusCode.OK,
            assertion = {}
        )

        // When
        val messageSyncApi: MessageSyncApi = MessageSyncApiV12(networkClient.httpClient)
        val response = messageSyncApi.fetchMessages(userId, null, null, null, size)

        // Then
        assertTrue(response.isSuccessful())
    }

    private companion object {
        const val PATH_STATE = "/state"
    }
}
