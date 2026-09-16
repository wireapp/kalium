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
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.fakefilesystem.FakeFileSystem

internal const val TEST_ENDPOINT = "https://cells.example.test"
internal const val TEST_ACCESS_TOKEN = "access-token"
internal const val TEST_GATEWAY_SECRET = "gateway-secret"
internal const val DEFAULT_TEST_MULTIPART_CHUNK_SIZE = 10 * 1024 * 1024L
internal const val REFRESHED_TOKEN_PREFIX = "refreshed-token-"

/**
 * Credentials provider that keeps track of how often the access token was read and refreshed.
 * A refresh rotates the token, mirroring a session whose token was updated by the session manager.
 */
internal class FakeS3CredentialsProvider : S3CredentialsProvider {

    var credentialsCount = 0
        private set
    var refreshCount = 0
        private set
    val rejectedAccessKeyIds = mutableListOf<String>()

    private var accessToken = TEST_ACCESS_TOKEN

    override suspend fun credentials(): S3Credentials {
        credentialsCount++
        return S3Credentials(accessToken, TEST_GATEWAY_SECRET)
    }

    override suspend fun refreshedCredentials(rejectedAccessKeyId: String): S3Credentials {
        refreshCount++
        rejectedAccessKeyIds += rejectedAccessKeyId
        accessToken = "$REFRESHED_TOKEN_PREFIX$refreshCount"
        return S3Credentials(accessToken, TEST_GATEWAY_SECRET)
    }
}

internal fun createClient(
    httpClient: HttpClient,
    fileSystem: FileSystem = FileSystem.SYSTEM,
    endpoint: String = TEST_ENDPOINT,
    credentialsProvider: S3CredentialsProvider = FakeS3CredentialsProvider(),
    config: CellsS3ClientConfig = fixedDateConfig(),
): CellsS3Client = CellsS3Client(
    httpClient = httpClient,
    endpointProvider = { endpoint },
    credentialsProvider = credentialsProvider,
    fileSystem = fileSystem,
    config = config,
)

internal fun createUploadFile(bytes: ByteArray): Pair<FakeFileSystem, Path> {
    val fileSystem = FakeFileSystem()
    val path = "/upload.txt".toPath()
    fileSystem.write(path) {
        write(bytes)
    }
    return fileSystem to path
}

internal fun cellNode(path: String): CellNodeDTO = CellNodeDTO(
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

internal fun fixedDateConfig(): CellsS3ClientConfig = CellsS3ClientConfig(
    dateProvider = { AwsSigningDate(date = "20260701", dateTime = "20260701T120102Z") },
)

internal fun fixedDateConfig(
    maxRegularUploadSize: Long,
    multipartChunkSize: Long = DEFAULT_TEST_MULTIPART_CHUNK_SIZE,
): CellsS3ClientConfig = CellsS3ClientConfig(
    dateProvider = { AwsSigningDate(date = "20260701", dateTime = "20260701T120102Z") },
    maxRegularUploadSize = maxRegularUploadSize,
    multipartChunkSize = multipartChunkSize,
)
