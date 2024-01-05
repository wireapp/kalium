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

internal class FeatureConfigApiV0Test : ApiTest() {

    @Test
    fun givenValidRequest_WhenCallingTheFeatureConfigApi_SuccessResponseExpected() = runTest {
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
    fun givenInValidRequestWithInsufficientPermission_WhenCallingTheFeatureConfigApi_ErrorResponseExpected() = runTest {
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
    fun givenInValidRequestWithNoTeam_WhenCallingFeatureConfigApi_ErrorResponseExpected() = runTest {
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
