package com.wire.kalium.api.tools.json.api.asset

import com.wire.kalium.api.ApiTest
import com.wire.kalium.network.api.asset.AssetApi
import com.wire.kalium.network.api.asset.AssetApiImp
import com.wire.kalium.network.api.model.AssetMetadata
import com.wire.kalium.network.api.model.AssetRetentionType
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class AssetApiTest : ApiTest {
    @Test
    fun givenAValidAssetUploadApiRequest_whenCallingTheAssetUploadApiEndpoint_theRequestShouldBeConfiguredCorrectly() = runTest {
        // Given
        val assetMetadata = AssetMetadata("image/jpeg", true, AssetRetentionType.ETERNAL, "md5-hash")
        val encryptedData = ByteArray(16)
        val httpClient = mockAuthenticatedHttpClient(
            VALID_ASSET_UPLOAD_RESPONSE.rawJson,
            statusCode = HttpStatusCode.Created,
            assertion = {
                assertPost()
                assertJson()
                assertNoQueryParams()
                assertAuthorizationHeaderExist()
                assertPathEqual(PATH_PUBLIC_ASSETS)
            }
        )

        // When
        val assetApi: AssetApi = AssetApiImp(httpClient)
        val response = assetApi.uploadAsset(assetMetadata, encryptedData)

        // Then
        assertTrue(response.isSuccessful())
        assertEquals(response.value, VALID_ASSET_UPLOAD_RESPONSE.serializableData)
    }

    @Test
    fun givenAValidAssetUploadApiRequest_whenCallingTheAssetUploadApiEndpoint_theRequestHeaderShouldContainTheAssetToken() = runTest {
        // Given
        val assetMetadata = AssetMetadata("image/jpeg", true, AssetRetentionType.ETERNAL, "md5-hash")
        val encryptedData = ByteArray(16)
        val httpClient = mockAuthenticatedHttpClient(
            VALID_ASSET_UPLOAD_RESPONSE.rawJson,
            statusCode = HttpStatusCode.Created,
            assertion = {
                assertPost()
                assertJson()
                assertNoQueryParams()
                assertAuthorizationHeaderExist()
                assertPathEqual(PATH_PUBLIC_ASSETS)
            }
        )

        // When
        val assetApi: AssetApi = AssetApiImp(httpClient)
        val response = assetApi.uploadAsset(assetMetadata, encryptedData)

        // Then
        assertTrue(response.isSuccessful())
        assertEquals(response.value, VALID_ASSET_UPLOAD_RESPONSE.serializableData)
    }

    @Test
    fun givenAnInvalidAssetUploadApiRequest_whenCallingTheAssetUploadApiEndpoint_theRequestShouldContainAnError() = runTest {
        // Given
        val assetMetadata = AssetMetadata("image/jpeg", true, AssetRetentionType.ETERNAL, "md5-hash")
        val encryptedData = ByteArray(16)
        val httpClient = mockAuthenticatedHttpClient(
            INVALID_ASSET_UPLOAD_RESPONSE.rawJson,
            statusCode = HttpStatusCode.BadRequest,
            assertion = {
                assertPost()
                assertJson()
                assertNoQueryParams()
                assertAuthorizationHeaderExist()
                assertPathEqual(PATH_PUBLIC_ASSETS)
            }
        )

        // When
        val assetApi: AssetApi = AssetApiImp(httpClient)
        val response = assetApi.uploadAsset(assetMetadata, encryptedData)

        // Then
        assertTrue(response is NetworkResponse.Error)
        assertTrue(response.kException is KaliumException.InvalidRequestError)
    }

    @Test
    fun givenAValidAssetDownloadApiRequest_whenCallingTheAssetDownloadApiEndpoint_theRequestShouldBeConfiguredCorrectly() = runTest {
        // Given
        val downloadedAsset = ByteArray(16)
        val assetKey = "3-1-e7788668-1b22-488a-b63c-acede42f771f"
        val assetToken = "assetToken"
        val apiPath = "$PATH_PUBLIC_ASSETS/$assetKey"
        val httpClient = mockAuthenticatedHttpClient(
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
        val assetApi: AssetApi = AssetApiImp(httpClient)
        val response = assetApi.downloadAsset(assetKey, assetToken)

        // Then
        assertTrue(response.isSuccessful())

        // We can't assert that the response object is exactly the same object as the provided on the Mocked HttpClient because MockEngine
        // from Ktor seems to copy internally the content of the original ByteArray into a different ByteArray, therefore they are not
        // technically the same object. That's why we are just doing a simple array size check
        assertEquals(response.value.size, downloadedAsset.size)
    }

    @Test
    fun givenAnInvalidAssetDownloadApiRequest_whenCallingTheAssetDownloadApiEndpoint_theResponseShouldContainAnError() = runTest {
        // Given
        val assetKey = "3-1-e7788668-1b22-488a-b63c-acede42f771f"
        val assetToken = "assetToken"
        val apiPath = "$PATH_PUBLIC_ASSETS/$assetKey"
        val httpClient = mockAuthenticatedHttpClient(
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
        val assetApi: AssetApi = AssetApiImp(httpClient)
        val response = assetApi.downloadAsset(assetKey, assetToken)

        // Then
        assertTrue(response is NetworkResponse.Error)
        assertTrue(response.kException is KaliumException.InvalidRequestError)
    }

    companion object {
        val VALID_ASSET_UPLOAD_RESPONSE = AssetUploadResponseJson.valid
        val INVALID_ASSET_UPLOAD_RESPONSE = AssetUploadResponseJson.invalid
        const val PATH_PUBLIC_ASSETS = "/assets/v3/"
        const val HEADER_ASSET_TOKEN = "Asset-Token"
    }
}
