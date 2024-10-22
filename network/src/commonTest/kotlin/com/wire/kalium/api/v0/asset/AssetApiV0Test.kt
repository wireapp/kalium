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

package com.wire.kalium.api.v0.asset

import com.wire.kalium.api.ApiTest
import com.wire.kalium.mocks.extensions.toJsonString
import com.wire.kalium.mocks.mocks.asset.AssetMocks
import com.wire.kalium.network.api.base.authenticated.asset.AssetApi
import com.wire.kalium.network.api.authenticated.asset.AssetMetadataRequest
import com.wire.kalium.network.api.model.AssetId
import com.wire.kalium.network.api.model.AssetRetentionType
import com.wire.kalium.network.api.v0.authenticated.AssetApiV0
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.Source
import okio.blackholeSink
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
internal class AssetApiV0Test : ApiTest() {
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
                assertPathEqual(PATH_ASSETS_V3)
            }
        )

        // When
        val assetApi: AssetApi = AssetApiV0(networkClient)
        val response = assetApi.uploadAsset(assetMetadata, encryptedDataSource, encryptedData.size.toLong())

        // Then
        assertTrue(response.isSuccessful())
        assertEquals(response.value, VALID_ASSET_UPLOAD_RESPONSE)
    }

    private fun getDummyDataSource(fileSystem: FakeFileSystem, dummyData: ByteArray): Source {
        val dummyPath = "some-data-path".toPath()
        fileSystem.write(dummyPath) {
            write(dummyData)
        }
        return fileSystem.source(dummyPath)
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
                assertPathEqual(PATH_ASSETS_V3)
            }
        )

        // When
        val assetApi: AssetApi = AssetApiV0(networkClient)
        val response = assetApi.uploadAsset(assetMetadata, encryptedDataSource, encryptedData.size.toLong())

        // Then
        assertTrue(response is NetworkResponse.Error)
        assertTrue(response.kException is KaliumException.InvalidRequestError)
    }

    @Test
    fun givenAValidAssetDownloadApiRequest_whenCallingTheAssetDownloadApiEndpoint_theRequestShouldBeConfiguredCorrectly() = runTest {
        // Given
        val downloadedAsset = "some-data".encodeToByteArray()
        val apiPath = "$PATH_ASSETS_V4/$ASSET_DOMAIN/$ASSET_KEY"
        val networkClient = mockAssetsHttpClient(
            responseBody = ByteReadChannel(downloadedAsset),
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertGet()
                assertNoQueryParams()
                assertAuthorizationHeaderExist()
                assertHeaderExist(HEADER_ASSET_TOKEN)
                assertPathEqual(apiPath)
            },
            headers = mapOf(HttpHeaders.ContentType to ContentType.Application.OctetStream.toString())
        )

        // When
        val assetApi: AssetApi = AssetApiV0(networkClient)
        val response = assetApi.downloadAsset(assetId.value, assetId.domain, ASSET_TOKEN, tempFileSink)
        assertIs<NetworkResponse.Success<Unit>>(response)
    }

    @Test
    fun givenAValidAssetDownloadApiRequest_whenCallingTheAssetDownloadWithoutADomain_theRequestShouldBeConfiguredToV3Fallback() = runTest {
        // Given
        val downloadedAsset = ByteArray(16)
        val networkClient = mockAuthenticatedNetworkClient(
            responseBody = downloadedAsset,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertGet()
                assertNoQueryParams()
                assertAuthorizationHeaderExist()
                assertHeaderExist(HEADER_ASSET_TOKEN)
                assertPathEqual("$PATH_ASSETS_V3/$ASSET_KEY")
            },
            headers = mapOf(HttpHeaders.ContentType to ContentType.Application.OctetStream.toString())
        )

        // When
        val assetApi: AssetApi = AssetApiV0(networkClient)
        val assetIdFallback = assetId.copy(domain = "")
        val response = assetApi.downloadAsset(assetIdFallback.value, assetIdFallback.domain, ASSET_TOKEN, tempFileSink)
        assertIs<NetworkResponse.Success<Unit>>(response)
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
                assertPathEqual("$PATH_ASSETS_V3/$ASSET_KEY")
            }
        )

        // When
        val assetApi: AssetApi = AssetApiV0(networkClient)
        val assetIdFallback = assetId.copy(domain = "")
        val response = assetApi.deleteAsset(assetIdFallback.value, assetIdFallback.domain, ASSET_TOKEN)

        // Then
        assertTrue(response.isSuccessful())
    }

    @Test
    fun givenAnInvalidAssetDownloadApiRequest_whenCallingTheAssetDownloadApiEndpoint_theResponseShouldContainAnError() = runTest {
        // Given
        val apiPath = "$PATH_ASSETS_V4/$ASSET_DOMAIN/$ASSET_KEY"
        val networkClient = mockAuthenticatedNetworkClient(
            responseBody = AssetMocks.invalid.toJsonString(),
            statusCode = HttpStatusCode.BadRequest,
            assertion = {
                assertGet()
                assertNoQueryParams()
                assertAuthorizationHeaderExist()
                assertPathEqual(apiPath)
            }
        )

        // When
        val assetApi: AssetApi = AssetApiV0(networkClient)
        val response = assetApi.downloadAsset(assetId.value, assetId.domain, ASSET_TOKEN, tempFileSink)
        assertIs<NetworkResponse.Error>(response)
        assertIs<KaliumException.InvalidRequestError>(response.kException)
    }

    companion object {
        val VALID_ASSET_UPLOAD_RESPONSE = AssetMocks.asset
        val INVALID_ASSET_UPLOAD_RESPONSE = AssetMocks.invalid
        const val PATH_ASSETS_V3 = "/assets/v3"
        const val PATH_ASSETS_V4 = "/assets/v4"
        const val HEADER_ASSET_TOKEN = "Asset-Token"
        const val ASSET_KEY = "3-1-e7788668-1b22-488a-b63c-acede42f771f"
        const val ASSET_DOMAIN = "wire.com"
        const val ASSET_TOKEN = "assetToken"
        val assetId: AssetId = AssetId(ASSET_KEY, ASSET_DOMAIN)
        val tempFileSink = blackholeSink()
    }
}
