package com.wire.kalium.api.api_version

import com.wire.kalium.api.ApiTest
import com.wire.kalium.model.VersionInfoDTOJson
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
class VersionApiTest : ApiTest {

    @Test
    fun givenSuccessResponse_whenFetchingSupportedRemoteVersion_thenRequestIsConfigureCorrectly() = runTest {
        val expected = ServerConfigDTO.MetaData(true, ApiVersionDTO.Valid(1), "wire.com")
        val httpClient = mockUnboundNetworkClient(
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
    fun given404Response_whenFetchingSupportedRemoteVersion_thenResultIsApiVersion0AndFederationFalse() = runTest {
        val expected = ServerConfigDTO.MetaData(false, ApiVersionDTO.Valid(0), null)
        val httpClient = mockUnboundNetworkClient(
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
