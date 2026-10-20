/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

package com.wire.kalium.api.v2

import com.wire.kalium.api.ApiTest
import com.wire.kalium.mocks.extensions.toJsonString
import com.wire.kalium.mocks.mocks.asset.AssetMocks
import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.kaliumLogger
import com.wire.kalium.network.api.authenticated.asset.AssetMetadataRequest
import com.wire.kalium.network.api.base.authenticated.asset.AssetApi
import com.wire.kalium.network.api.model.AssetId
import com.wire.kalium.network.api.model.AssetRetentionType
import com.wire.kalium.network.api.model.UserId
import com.wire.kalium.network.api.v2.authenticated.AssetApiV2
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.http.headersOf
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.Path.Companion.toPath
import okio.Source
import okio.blackholeSink
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class AssetApiV2Test : ApiTest() {

    private val userId: UserId = UserId("user_id", "domain")

    @Test
    fun concurrentUploadsReplayCompleteBodiesAfterAuthenticationChallenge() = runTest {
        val firstBodies = mutableMapOf<String, ByteArray>()
        val retryBodies = mutableMapOf<String, ByteArray>()
        // The engine handles the concurrent uploads on several threads.
        val bodiesLock = Mutex()
        var refreshes = 0
        val engine = MockEngine { request ->
            val body = request.body.toByteArray()
            val text = body.decodeToString()
            val asset = (0 until 6).map { "fixture-asset-$it" }.single { it in text }
            if (request.headers["Authorization"] == "Bearer expired") {
                bodiesLock.withLock { firstBodies[asset] = body }
                respond("", HttpStatusCode.Unauthorized, headersOf("WWW-Authenticate", "Bearer"))
            } else {
                bodiesLock.withLock { retryBodies[asset] = body }
                respond(
                    VALID_ASSET_UPLOAD_RESPONSE.toJsonString(),
                    HttpStatusCode.Created,
                    headersOf("Content-Type", "application/json")
                )
            }
        }
        val client = AuthenticatedNetworkClient(
            engine = engine,
            serverConfigDTO = TEST_SESSION_MANAGER.serverConfig(),
            bearerAuthProvider = BearerAuthProvider(
                refreshTokens = {
                    refreshes++
                    BearerTokens("renewed", "refresh")
                },
                loadTokens = { BearerTokens("expired", "refresh") },
                sendWithoutRequestCallback = { true },
                realm = null
            ),
            kaliumLogger = kaliumLogger
        )
        try {
            val api = AssetApiV2(client, userId)
            (0 until 6).map { index ->
                async {
                    val payload = "fixture-asset-$index".encodeToByteArray()
                    val response = api.uploadAsset(
                        AssetMetadataRequest("image/png", false, AssetRetentionType.PERSISTENT, "md5"),
                        { Buffer().write(payload) }, payload.size.toLong()
                    )
                    assertTrue(response.isSuccessful())
                }
            }.awaitAll()
            assertEquals(6, retryBodies.size)
            assertTrue(firstBodies.isNotEmpty())
            assertTrue(refreshes > 0)
            firstBodies.forEach { (asset, body) -> assertContentEquals(body, retryBodies[asset]) }
            retryBodies.values.forEach { body ->
                val text = body.decodeToString()
                assertTrue(text.startsWith("--frontier\r\n"))
                assertTrue(text.endsWith("\r\n--frontier--\r\n"))
            }
        } finally {
            client.httpClient.close()
        }
    }

    @Test
    fun givenAValidAssetUploadApiRequest_whenCallingTheAssetUploadApiEndpoint_theRequestShouldBeConfiguredCorrectly() = runTest {
        // Given
        val fileSystem = FakeFileSystem()
        val assetMetadata = AssetMetadataRequest("image/jpeg", true, AssetRetentionType.ETERNAL, "md5-hash")
        val encryptedData = "some-data".encodeToByteArray()
        val encryptedDataSource = { getDummyDataSource(fileSystem, encryptedData) }
        val networkClient = mockAuthenticatedNetworkClient(
            VALID_ASSET_UPLOAD_RESPONSE.toJsonString(),
            statusCode = HttpStatusCode.Created,
            assertion = {
                assertPost()
                assertNoQueryParams()
                assertAuthorizationHeaderExist()
                assertPathEqual(PATH_ASSETS)
            }
        )

        // When
        val assetApi: AssetApi = AssetApiV2(networkClient, userId)
        val response = assetApi.uploadAsset(assetMetadata, encryptedDataSource, encryptedData.size.toLong())

        // Then
        assertTrue(response.isSuccessful())
        assertEquals(response.value, VALID_ASSET_UPLOAD_RESPONSE)
    }

    @Test
    fun givenAnInvalidAssetUploadApiRequest_whenCallingTheAssetUploadApiEndpoint_theRequestShouldContainAnError() = runTest {
        // Given
        val fileSystem = FakeFileSystem()
        val assetMetadata = AssetMetadataRequest("image/jpeg", true, AssetRetentionType.ETERNAL, "md5-hash")
        val encryptedData = "some-data".encodeToByteArray()
        val encryptedDataSource = { getDummyDataSource(fileSystem, encryptedData) }
        val networkClient = mockAuthenticatedNetworkClient(
            INVALID_ASSET_UPLOAD_RESPONSE.toJsonString(),
            statusCode = HttpStatusCode.BadRequest,
            assertion = {
                assertPost()
                assertNoQueryParams()
                assertAuthorizationHeaderExist()
                assertPathEqual(PATH_ASSETS)
            }
        )

        // When
        val assetApi: AssetApi = AssetApiV2(networkClient, userId)
        val response = assetApi.uploadAsset(assetMetadata, encryptedDataSource, encryptedData.size.toLong())

        // Then
        assertTrue(response is NetworkResponse.Error)
        assertTrue(response.kException is KaliumException.InvalidRequestError)
    }

    @Test
    fun givenAValidAssetDownloadApiRequest_whenCallingTheAssetDownloadApiEndpoint_theRequestShouldBeConfiguredCorrectly() = runTest {
        // Given
        val downloadedAsset = "some-data".encodeToByteArray()
        val apiPath = "$PATH_ASSETS/$ASSET_DOMAIN/$ASSET_KEY"
        val networkClient = mockAssetsHttpClient(
            responseBody = ByteReadChannel(downloadedAsset),
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertGet()
                assertNoQueryParams()
                assertAuthorizationHeaderExist()
                assertHeaderExist(HEADER_ASSET_TOKEN)
                assertPathEqual(apiPath)
            }
        )

        // When
        val assetApi: AssetApi = AssetApiV2(networkClient, userId)
        val response = assetApi.downloadAsset(assetId.value, assetId.domain, ASSET_TOKEN, tempFileSink)
    }

    @Test
    fun givenAValidAssetDeleteApiRequest_whenCallingTheAssetDeleteWithoutADomain_thenRequestShouldBeConfiguredToV3Fallback() = runTest {
        // Given
        val mockedBody = ByteArray(16)
        val networkClient = mockAuthenticatedNetworkClient(
            responseBody = mockedBody,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertDelete()
                assertNoQueryParams()
                assertAuthorizationHeaderExist()
                assertHeaderExist(HEADER_ASSET_TOKEN)
                assertPathEqual("$PATH_ASSETS/$ASSET_DOMAIN/$ASSET_KEY")
            }
        )

        // When
        val assetApi: AssetApi = AssetApiV2(networkClient, userId)
        val assetIdFallback = assetId
        val response = assetApi.deleteAsset(assetIdFallback.value, assetIdFallback.domain, ASSET_TOKEN)

        // Then
        assertTrue(response.isSuccessful())
    }

    private fun getDummyDataSource(fileSystem: FakeFileSystem, dummyData: ByteArray): Source {
        val dummyPath = "some-data-path".toPath()
        fileSystem.write(dummyPath) {
            write(dummyData)
        }
        return fileSystem.source(dummyPath)
    }

    companion object {
        const val PATH_ASSETS = "/assets"
        const val HEADER_ASSET_TOKEN = "Asset-Token"
        const val ASSET_KEY = "3-1-e7788668-1b22-488a-b63c-acede42f771f"
        const val ASSET_DOMAIN = "wire.com"
        const val ASSET_TOKEN = "assetToken"
        val VALID_ASSET_UPLOAD_RESPONSE = AssetMocks.asset
        val INVALID_ASSET_UPLOAD_RESPONSE = AssetMocks.invalid
        val assetId: AssetId = AssetId(ASSET_KEY, ASSET_DOMAIN)
        val tempFileSink = blackholeSink()
    }
}
