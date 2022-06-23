package com.wire.kalium.api.tools.json.api.featureConfig

import com.wire.kalium.api.ApiTest
import com.wire.kalium.network.api.featureConfigs.FeatureConfigApi
import com.wire.kalium.network.api.featureConfigs.FeatureConfigApiImpl
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class FeatureConfigApiTest : ApiTest {

    @Test
    fun givenValidRequest_WhenCallingTheFileSharingApi_SuccessResponseExpected() = runTest {
        // Given
        val apiPath = FEATURE_CONFIG
        val networkClient = mockAuthenticatedNetworkClient(
            responseBody = FeatureConfigJson.featureConfigResponseSerializerResponse.rawJson,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertGet()
                assertNoQueryParams()
                assertAuthorizationHeaderExist()
                assertPathEqual(apiPath)
            }
        )

        // When
        val assetApi: FeatureConfigApi = FeatureConfigApiImpl(networkClient)
        val response = assetApi.featureConfigs()

        // Then
        assertTrue(response is NetworkResponse.Success)
    }

    @Test
    fun givenInValidRequestWithInsufficientPermission_WhenCallingTheFileSharingApi_ErrorResponseExpected() = runTest {
        // Given
        val apiPath = FEATURE_CONFIG
        val networkClient = mockAuthenticatedNetworkClient(
            responseBody = FeatureConfigJson.insufficientPermissionsErrorResponse.rawJson,
            statusCode = HttpStatusCode.Forbidden,
            assertion = {
                assertGet()
                assertNoQueryParams()
                assertAuthorizationHeaderExist()
                assertPathEqual(apiPath)
            }
        )

        // When
        val assetApi: FeatureConfigApi = FeatureConfigApiImpl(networkClient)
        val response = assetApi.featureConfigs()

        // Then
        assertTrue(response is NetworkResponse.Error)
        assertTrue(response.kException is KaliumException.InvalidRequestError)
    }

    @Test
    fun givenInValidRequestWithNoTeam_WhenCallingTheFileSharingApi_ErrorResponseExpected() = runTest {
        // Given
        val apiPath = FEATURE_CONFIG
        val networkClient = mockAuthenticatedNetworkClient(
            responseBody = FeatureConfigJson.teamNotFoundErrorResponse.rawJson,
            statusCode = HttpStatusCode.NotFound,
            assertion = {
                assertGet()
                assertNoQueryParams()
                assertAuthorizationHeaderExist()
                assertPathEqual(apiPath)
            }
        )

        // When
        val assetApi: FeatureConfigApi = FeatureConfigApiImpl(networkClient)
        val response = assetApi.featureConfigs()

        // Then
        assertTrue(response is NetworkResponse.Error)
        assertTrue(response.kException is KaliumException.InvalidRequestError)
    }

    companion object {
        const val FEATURE_CONFIG = "feature-configs"
    }
}
