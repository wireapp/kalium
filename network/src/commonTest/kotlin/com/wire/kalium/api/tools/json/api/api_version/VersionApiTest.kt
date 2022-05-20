package com.wire.kalium.api.tools.json.api.api_version

import com.wire.kalium.api.ApiTest
import com.wire.kalium.network.api.versioning.VersionApi
import com.wire.kalium.network.api.versioning.VersionApiImpl
import com.wire.kalium.network.api.versioning.VersionInfoDTO
import com.wire.kalium.network.utils.NetworkResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@Ignore
@OptIn(ExperimentalCoroutinesApi::class)
class VersionApiTest : ApiTest {

    @Test
    fun givenSuccessResponse_whenFetchingSupportedRemoteVersion_thenRequestIsConfigureCorrectly() = runTest {

        val expected = VersionInfoDTOJson.valid.serializableData
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

        val versionApi: VersionApi = VersionApiImpl(TODO())
        versionApi.fetchApiVersion(Url("https://wire.de")).also { actual ->
            assertIs<NetworkResponse.Success<VersionInfoDTO>>(actual)
            assertEquals(expected, actual.value)
        }
    }

    @Ignore
    @Test
    fun given404Response_whenFetchingSupportedRemoteVersion_thenResultIsApiVersion0AndFederationFalse() = runTest {
        val expected = VersionInfoDTOJson.valid404Result
        val httpClient = mockUnauthenticatedNetworkClient(
            responseBody = "can be what ever",
            statusCode = HttpStatusCode.NotFound
        )

        val versionApi: VersionApi = VersionApiImpl(TODO())
        versionApi.fetchApiVersion(Url("https://wire.de")).also { actual ->
            assertIs<NetworkResponse.Success<VersionInfoDTO>>(actual)
            assertEquals(expected, actual.value)
        }
    }
}
