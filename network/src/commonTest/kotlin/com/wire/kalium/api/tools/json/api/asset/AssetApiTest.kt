package com.wire.kalium.api.tools.json.api.asset

import com.wire.kalium.api.ApiTest
import com.wire.kalium.network.api.AssetId
import com.wire.kalium.network.api.asset.AssetApi
import com.wire.kalium.network.api.asset.AssetApiImpl
import com.wire.kalium.network.api.asset.AssetMetadataRequest
import com.wire.kalium.network.api.model.AssetRetentionType
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class AssetApiTest : ApiTest {
    @Test
    fun givenAValidAssetUploadApiRequest_whenCallingTheAssetUploadApiEndpoint_theRequestShouldBeConfiguredCorrectly() = runTest {
        // Given
        val assetMetadata = AssetMetadataRequest("image/jpeg", true, AssetRetentionType.ETERNAL, "md5-hash")
        val encryptedData = ByteArray(16)
        Random.nextBytes(encryptedData)
        val networkClient = mockAuthenticatedNetworkClient(
            VALID_ASSET_UPLOAD_RESPONSE.rawJson,
            statusCode = HttpStatusCode.Created,
            assertion = {
                assertPost()
                assertNoQueryParams()
                assertAuthorizationHeaderExist()
                assertPathEqual(PATH_PUBLIC_ASSETS)
            }
        )

        // When
        val assetApi: AssetApi = AssetApiImpl(networkClient)
        val response = assetApi.uploadAsset(assetMetadata, encryptedData)

        // Then
        assertTrue(response.isSuccessful())
        assertEquals(response.value, VALID_ASSET_UPLOAD_RESPONSE.serializableData)
    }

    @Test
    fun givenAValidAssetUploadApiRequest_whenCallingTheAssetUploadApiEndpoint_theRequestHeaderShouldContainTheAssetToken() = runTest {
        // Given
        val assetMetadata = AssetMetadataRequest("image/jpeg", true, AssetRetentionType.ETERNAL, "md5-hash")
        val encryptedData = ByteArray(16)
        Random.nextBytes(encryptedData)
        val networkClient = mockAuthenticatedNetworkClient(
            VALID_ASSET_UPLOAD_RESPONSE.rawJson,
            statusCode = HttpStatusCode.Created,
            assertion = {
                assertPost()
                assertNoQueryParams()
                assertAuthorizationHeaderExist()
                assertPathEqual(PATH_PUBLIC_ASSETS)
            }
        )

        // When
        val assetApi: AssetApi = AssetApiImpl(networkClient)
        val response = assetApi.uploadAsset(assetMetadata, encryptedData)

        // Then
        assertTrue(response.isSuccessful())
        assertEquals(response.value, VALID_ASSET_UPLOAD_RESPONSE.serializableData)
    }

    @Test
    fun givenAnInvalidAssetUploadApiRequest_whenCallingTheAssetUploadApiEndpoint_theRequestShouldContainAnError() = runTest {
        // Given
        val assetMetadata = AssetMetadataRequest("image/jpeg", true, AssetRetentionType.ETERNAL, "md5-hash")
        val encryptedData = ByteArray(16)
        Random.nextBytes(encryptedData)
        val networkClient = mockAuthenticatedNetworkClient(
            INVALID_ASSET_UPLOAD_RESPONSE.rawJson,
            statusCode = HttpStatusCode.BadRequest,
            assertion = {
                assertPost()
                assertNoQueryParams()
                assertAuthorizationHeaderExist()
                assertPathEqual(PATH_PUBLIC_ASSETS)
            }
        )

        // When
        val assetApi: AssetApi = AssetApiImpl(networkClient)
        val response = assetApi.uploadAsset(assetMetadata, encryptedData)

        // Then
        assertTrue(response is NetworkResponse.Error)
        assertTrue(response.kException is KaliumException.InvalidRequestError)
    }

    @Test
    fun givenAValidAssetDownloadApiRequest_whenCallingTheAssetDownloadApiEndpoint_theRequestShouldBeConfiguredCorrectly() = runTest {
        // Given
        val downloadedAsset = ByteArray(16)
        Random.nextBytes(downloadedAsset)
        val apiPath = "$PATH_DOWNLOAD_ASSETS_V4/$ASSET_DOMAIN/$ASSET_KEY"
        val networkClient = mockAuthenticatedNetworkClient(
            responseBody = downloadedAsset,
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
        val assetApi: AssetApi = AssetApiImpl(networkClient)
        val response = assetApi.downloadAsset(assetId, ASSET_TOKEN)

        // Then
        assertTrue(response.isSuccessful())
        assertEquals(response.value.decodeToString(), downloadedAsset.decodeToString())
    }

    @Test
    fun givenAValidAssetDownloadApiRequest_whenCallingTheAssetDownloadWithoutADomaino_theRequestShouldBeConfiguredToV3Fallback() = runTest {
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
                assertPathEqual("$PATH_PUBLIC_ASSETS/$ASSET_KEY")
            }
        )

        // When
        val assetApi: AssetApi = AssetApiImpl(networkClient)
        val assetIdFallback = assetId.copy(domain = "")
        val response = assetApi.downloadAsset(assetIdFallback, ASSET_TOKEN)

        // Then
        assertTrue(response.isSuccessful())
        assertEquals(response.value.decodeToString(), downloadedAsset.decodeToString())
    }

    @Test
    fun givenAnInvalidAssetDownloadApiRequest_whenCallingTheAssetDownloadApiEndpoint_theResponseShouldContainAnError() = runTest {
        // Given
        val apiPath = "$PATH_DOWNLOAD_ASSETS_V4/$ASSET_DOMAIN/$ASSET_KEY"
        val networkClient = mockAuthenticatedNetworkClient(
            responseBody = AssetDownloadResponseJson.invalid.rawJson,
            statusCode = HttpStatusCode.BadRequest,
            assertion = {
                assertGet()
                assertNoQueryParams()
                assertAuthorizationHeaderExist()
                assertPathEqual(apiPath)
            }
        )

        // When
        val assetApi: AssetApi = AssetApiImpl(networkClient)
        val response = assetApi.downloadAsset(assetId, ASSET_TOKEN)

        // Then
        assertTrue(response is NetworkResponse.Error)
        assertTrue(response.kException is KaliumException.InvalidRequestError)
    }

    companion object {
        val VALID_ASSET_UPLOAD_RESPONSE = AssetUploadResponseJson.valid
        val INVALID_ASSET_UPLOAD_RESPONSE = AssetUploadResponseJson.invalid
        const val PATH_PUBLIC_ASSETS = "/assets/v3"
        const val PATH_DOWNLOAD_ASSETS_V4 = "/assets/v4"
        const val HEADER_ASSET_TOKEN = "Asset-Token"
        const val ASSET_KEY = "3-1-e7788668-1b22-488a-b63c-acede42f771f"
        const val ASSET_DOMAIN = "wire.com"
        const val ASSET_TOKEN = "assetToken"
        val assetId: AssetId = AssetId(ASSET_KEY, ASSET_DOMAIN)
    }
}
