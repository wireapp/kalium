package com.wire.kalium.api.v2

import com.wire.kalium.api.ApiTest
import com.wire.kalium.network.api.base.authenticated.asset.AssetApi
import com.wire.kalium.network.api.base.model.AssetId
import com.wire.kalium.network.api.v2.authenticated.AssetApiV2
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okio.blackholeSink
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AssetApiV2Test : ApiTest {

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
        val assetApi: AssetApi = AssetApiV2(networkClient)
        val response = assetApi.downloadAsset(assetId, ASSET_TOKEN, tempFileSink)
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
        val assetApi: AssetApi = AssetApiV2(networkClient)
        val assetIdFallback = assetId
        val response = assetApi.deleteAsset(assetIdFallback, ASSET_TOKEN)

        // Then
        assertTrue(response.isSuccessful())
    }

    companion object {
        const val PATH_ASSETS = "/assets"
        const val HEADER_ASSET_TOKEN = "Asset-Token"
        const val ASSET_KEY = "3-1-e7788668-1b22-488a-b63c-acede42f771f"
        const val ASSET_DOMAIN = "wire.com"
        const val ASSET_TOKEN = "assetToken"
        val assetId: AssetId = AssetId(ASSET_KEY, ASSET_DOMAIN)
        val tempFileSink = blackholeSink()
    }
}
