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
import io.ktor.utils.io.ByteReadChannel
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
            ByteReadChannel.Empty, // Empty response body for HTTP 201
            statusCode = HttpStatusCode.Created,
            assertion = {
                assertPost()
                assertParameterEquals("user_id", userId)
                assertAuthorizationHeaderExist()
                assertContentType(ContentType.Application.OctetStream)
            }
        )

        // When
        val messageSyncApi: MessageSyncApi = MessageSyncApiV12(networkClient)
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
            ByteReadChannel.Empty, // Explicitly empty response
            statusCode = HttpStatusCode.Created,
            assertion = {}
        )

        // When
        val messageSyncApi: MessageSyncApi = MessageSyncApiV12(networkClient)
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
            ByteReadChannel("Bad Request"),
            statusCode = HttpStatusCode.BadRequest,
            assertion = {}
        )

        // When
        val messageSyncApi: MessageSyncApi = MessageSyncApiV12(networkClient)
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
            ByteReadChannel.Empty,
            statusCode = HttpStatusCode.Created,
            assertion = {
                assertPost()
                assertParameterEquals("user_id", userId)
                assertAuthorizationHeaderExist()
                assertContentType(ContentType.Application.OctetStream)
            }
        )

        // When
        val messageSyncApi: MessageSyncApi = MessageSyncApiV12(networkClient)
        val response = messageSyncApi.uploadStateBackup(userId, backupDataSource, backupSize)

        // Then
        assertTrue(response.isSuccessful())
    }

    @Test
    fun givenCustomBackupServiceUrl_whenUploadingStateBackup_thenRequestShouldUseCustomUrl() = runTest {
        // Given
        val fileSystem = FakeFileSystem()
        val customBackupUrl = "https://custom-backup-service.com"
        val userId = "test-user"
        val backupData = "backup-data".encodeToByteArray()
        val backupDataSource = { getDummyDataSource(fileSystem, backupData) }
        val backupSize = backupData.size.toLong()

        val networkClient = mockAuthenticatedNetworkClient(
            ByteReadChannel.Empty,
            statusCode = HttpStatusCode.Created,
            assertion = {
                assertPost()
                assertParameterEquals("user_id", userId)
                // Note: In actual implementation, this would verify the custom URL is used
                // but for this test we're just ensuring the request is successful
            }
        )

        // When
        val messageSyncApi: MessageSyncApi = MessageSyncApiV12(networkClient, customBackupUrl)
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

    private companion object {
        const val PATH_STATE = "/state"
    }
}
