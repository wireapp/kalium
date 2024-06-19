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

package com.wire.kalium.api.common

import com.wire.kalium.api.ApiTest
import com.wire.kalium.mocks.responses.VersionInfoDTOJson
import com.wire.kalium.network.api.base.unbound.versioning.VersionApi
import com.wire.kalium.network.api.base.unbound.versioning.VersionApiImpl
import com.wire.kalium.network.tools.ApiVersionDTO
import com.wire.kalium.network.tools.ServerConfigDTO
import com.wire.kalium.network.utils.NetworkResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
internal class VersionApiTest : ApiTest() {

    @Test
    fun givenSuccessResponse_whenFetchingSupportedRemoteVersion_thenRequestIsConfigureCorrectly() = runTest {
        val expected = ServerConfigDTO.MetaData(true, ApiVersionDTO.Valid(1), "wire.com")
        val httpClient = mockUnauthenticatedNetworkClient(
            responseBody = VersionInfoDTOJson.valid.rawJson,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertJson()
                assertPathEqual("/api-version")
                assertGet()
                assertNoQueryParams()
            }
        )

        val versionApi: VersionApi = VersionApiImpl(httpClient, false)
        versionApi.fetchApiVersion(Url("https://wire.de")).also { actual ->
            assertIs<NetworkResponse.Success<ServerConfigDTO.MetaData>>(actual)
            assertEquals(expected, actual.value)
        }
    }

    @Test
    fun givenDevelopmentApiEnabled_whenFetchingSupportedRemoteVersion_thenResultIsApiVersion2() = runTest {
        val expected = ServerConfigDTO.MetaData(true, ApiVersionDTO.Valid(2), "wire.com")
        val httpClient = mockUnauthenticatedNetworkClient(
            responseBody = VersionInfoDTOJson.valid.rawJson,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertJson()
                assertPathEqual("/api-version")
                assertGet()
                assertNoQueryParams()
            }
        )

        val versionApi: VersionApi = VersionApiImpl(httpClient, true)
        versionApi.fetchApiVersion(Url("https://wire.de")).also { actual ->
            assertIs<NetworkResponse.Success<ServerConfigDTO.MetaData>>(actual)
            assertEquals(expected, actual.value)
        }
    }

    @Test
    fun given404Response_whenFetchingSupportedRemoteVersion_thenResultIsApiVersion0AndFederationFalse() = runTest {
        val expected = ServerConfigDTO.MetaData(false, ApiVersionDTO.Valid(0), null)
        val httpClient = mockUnauthenticatedNetworkClient(
            responseBody = "",
            statusCode = HttpStatusCode.NotFound
        )

        val versionApi: VersionApi = VersionApiImpl(httpClient, false)
        versionApi.fetchApiVersion(Url("https://wire.de")).also { actual ->
            assertIs<NetworkResponse.Success<ServerConfigDTO.MetaData>>(actual)
            assertEquals(expected, actual.value)
        }
    }
}
