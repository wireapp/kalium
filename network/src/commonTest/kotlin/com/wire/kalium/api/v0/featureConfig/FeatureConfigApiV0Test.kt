package com.wire.kalium.api.v0.featureConfig

import com.wire.kalium.api.ApiTest
import com.wire.kalium.model.FeatureConfigJson
import com.wire.kalium.network.api.base.authenticated.featureConfigs.FeatureConfigApi
import com.wire.kalium.network.api.v0.authenticated.FeatureConfigApiV0
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class FeatureConfigApiV0Test : ApiTest {

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
        val featureConfigApi: FeatureConfigApi = FeatureConfigApiV0(networkClient)
        val response = featureConfigApi.featureConfigs()

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
        val featureConfigApi: FeatureConfigApi = FeatureConfigApiV0(networkClient)
        val response = featureConfigApi.featureConfigs()

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
        val featureConfigApi: FeatureConfigApi = FeatureConfigApiV0(networkClient)
        val response = featureConfigApi.featureConfigs()

        // Then
        assertTrue(response is NetworkResponse.Error)
        assertTrue(response.kException is KaliumException.InvalidRequestError)
    }

    companion object {
        const val FEATURE_CONFIG = "feature-configs"
    }
}
