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
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

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
            dateProvider = { AwsSigningDate(date = "20260701", dateTime = "20260701T120102Z") },
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
    fun givenObjectKey_whenGettingPreSignedUrl_thenUrlContainsSigV4QueryParameters() = runTest {
        val client = CellsS3Client(
            httpClient = HttpClient(MockEngine { respond(content = "", status = HttpStatusCode.OK) }),
            endpointProvider = { "https://cells.example.test" },
            credentialsProvider = { S3Credentials("access-token", "gateway-secret") },
            dateProvider = { AwsSigningDate(date = "20260701", dateTime = "20260701T120102Z") },
        )

        val result = client.getPreSignedUrl("folder/a file.txt")

        assertContains(result, "https://cells.example.test/io/folder/a%20file.txt?")
        assertContains(result, "X-Amz-Algorithm=AWS4-HMAC-SHA256")
        assertContains(result, "X-Amz-Credential=access-token%2F20260701%2Fus-east-1%2Fs3%2Faws4_request")
        assertContains(result, "X-Amz-Date=20260701T120102Z")
        assertContains(result, "X-Amz-Expires=86400")
        assertContains(result, "X-Amz-SignedHeaders=host")
        assertContains(result, "X-Amz-Signature=")
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
}
